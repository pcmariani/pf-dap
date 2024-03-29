import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

def NEWLINE = System.lineSeparator()
def IFS = /\|\^\|/  // Input Field Separator
def OFS = "|^|"  // Output Field Separater

def sectionNumber = ExecutionUtil.getDynamicProcessProperty("DPP_SectionNumber") ?: "0.0.0.0.0"

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);
    
    // int reportContentItemId = (props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem_DynamicTableId") ?: "1") as int
    // int tableDefinitionId = (props.getProperty("document.dynamic.userdefined.ddp_TableDefinitionId") ?: "1") as int
    def sqlParamValues = props.getProperty("document.dynamic.userdefined.ddp_sqlParamValues")
    def sqlColumnNames = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnNames")
    int tableInstanceIndex = (props.getProperty("document.dynamic.userdefined.ddp_tableInstanceIndex") ?: "1") as int
    int tableInstanceId = props.getProperty("document.dynamic.userdefined.ddp_TableInstanceId") as int
    def virtualColumnsJson = props.getProperty("document.dynamic.userdefined.ddp_VirtualColumns")
    def virtualColumns = virtualColumnsJson ? new JsonSlurper().parseText(virtualColumnsJson).Records : []
    // println prettyJson(virtualColumns)

    // Virtual Columns
    def sqlColumnNamesArr = sqlColumnNames.split(IFS) as ArrayList
    // println sqlColumnNamesArr
    def virtualColumnsMap = [:]
    if (virtualColumns) {
        virtualColumns.each { vc ->
            // println vc
            def virtualColumnValue = vc.VirtualColumnRows?.find {it.TableInstanceId == tableInstanceId}?.Value
            // println virtualColumnValue
            int columnToInsertAfterIndex = sqlColumnNamesArr.indexOf(vc.ColumnToInsertAfter) + 1
            // println columnToInsertAfterIndex
            if (virtualColumnValue) {
                virtualColumnsMap[columnToInsertAfterIndex] = virtualColumnValue
            }
            // Add VirtualColumn Label to column names
            sqlColumnNamesArr.add(columnToInsertAfterIndex, vc.ColumnLabel)
        }
    }
    // println sqlColumnNamesArr

    // here begins a hack for sorting when one of the sql params is an IN operator and we're in a multiselect situation
    // if so, scan the sqlParamValues for an item with commas and set that as the sortKeysArr
    def sqlParamValuesArr = sqlParamValues?.split(/\s*;\s*/)
    def sortKeysArr = (sqlParamValuesArr.find { it.contains(",") })?.split(/\s*,\s*/) as ArrayList
    // println sortKeysArr

    def reader = new BufferedReader(new InputStreamReader(is))

    // def dataMap = [:]
    def outData = new StringBuilder()
    // def lineIndex = 0
    while ((line = reader.readLine()) != null ) {

        def lineArr = line
            .replaceFirst(/$IFS\s*$/, "${IFS}LAST_ELEMENT_IS_BLANK")
            .split(/\s*$IFS\s*/)
            .collect{ it == "LAST_ELEMENT_IS_BLANK" ? "" : it }

        // Add VirtualColumn value to lineArr
        virtualColumnsMap.each { columnToInsertAfterIndex, virtualColumnValue ->
            lineArr.add(columnToInsertAfterIndex, virtualColumnValue)
        }
        // println lineArr

        // Create the Data Table Location
        def dataTableLocation = sectionNumber + "-" + tableInstanceIndex.toString() + "###" + sqlParamValues
        outData.append((lineArr + dataTableLocation).join(OFS) + NEWLINE)

        // Add sqlParamValues to be parsed and included in an id.
        // This id will become the value of the attribute "link" on the td for this cell
        // which is a reference to the tableGroup with the same id
        // lineArr += dataTableLocation + "###" + sqlParamValues
        // println lineArr

        // if (sortKeysArr) {
        //     // find the item in the sortKeysArr which exists in this line make it the key for the line
        //     // the value will be an array of lines in case you had multiple lines for one key
        //     lineArr.collect {
        //         if (it in sortKeysArr) {
        //             if (dataMap[it]) dataMap[it] << lineArr
        //             else dataMap[it] = [lineArr]
        //         }
        //     }
        // }
        // else {
        //     // if we don't have a sortKeysList, just use an index for the map key to make it work with the map
        //     dataMap[lineIndex] = [lineArr]
        // }
        // lineIndex++
    }
    // println outData.toString()

    // // sort rows
    // if (sortKeysArr) {
    //     dataMap = dataMap.sort { sortKeysArr.indexOf(it.key) }
    // }

    // /* OUTPUT */
    // def outData = new StringBuilder()
    // dataMap.each { k, v ->
    //     v.each {
    //         outData.append(it.join(OFS) + NEWLINE)
    //     }
    // }

    props.setProperty("document.dynamic.userdefined.ddp_sqlColumnNames", sqlColumnNamesArr.join(OFS))

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);

}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
