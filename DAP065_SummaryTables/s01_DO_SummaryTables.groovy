import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

def NEWLINE = System.lineSeparator()
def IFS = /\|\^\|/  // Input Field Separator
def OFS = "|^|"  // Output Field Separater
def DBIFS = "\\^\\^\\^"    // Database Field Separator
def DBOFS = "^^^"    // Database Field Separator

def sectionNumber = ExecutionUtil.getDynamicProcessProperty("DPP_SectionNumber") ?: "0.0.0.0.0"

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    // int reportContentItemId = (props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem_DynamicTableId") ?: "1") as int
    // int tableDefinitionId = (props.getProperty("document.dynamic.userdefined.ddp_TableDefinitionId") ?: "1") as int
    def sqlParamValues = props.getProperty("document.dynamic.userdefined.ddp_sqlParamValues")
    // println sqlParamValues
    def sqlColumnNames = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnNames")
    // println sqlColumnNames
    def sqlColumnNamesArr = sqlColumnNames.split(IFS) as ArrayList
    // println sqlColumnNamesArr
    int tableInstanceIndex = (props.getProperty("document.dynamic.userdefined.ddp_tableInstanceIndex") ?: "1") as int
    // println tableInstanceIndex
    int tableInstanceId = props.getProperty("document.dynamic.userdefined.ddp_TableInstanceId") as int
    // println tableInstanceId
    def virtualColumnsJson = props.getProperty("document.dynamic.userdefined.ddp_VirtualColumns")
    def virtualColumns = virtualColumnsJson ? new JsonSlurper().parseText(virtualColumnsJson).Records : []
    // println prettyJson(virtualColumns)
    def sqlParamUserInputValuesJson = props.getProperty("document.dynamic.userdefined.ddp_sqlParamUserInputValuesJson")
    def sqlParamUserInputValues = sqlParamUserInputValuesJson ? new JsonSlurper().parseText(sqlParamUserInputValuesJson) : []
    // println prettyJson(sqlParamUserInputValues)

    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated")
    def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson)?.Records : []
    // println prettyJson(pivotedDataConfigsArr)
    activePivotOnKeysArrsArr = pivotedDataConfigsArr.findAll{it.Active == true}.ColumnKey.collect{ it.toUpperCase().split(DBIFS).findAll{ it!=""} }.transpose()
    // println activePivotOnKeysArrsArr

    def groupByConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_GroupByConfigsConsolidated")
    def groupByConfigsArr = groupByConfigsJson ? new JsonSlurper().parseText(groupByConfigsJson)?.Records : []
    // println prettyJson(groupByConfigsArr)
    activeGroupByKeysArrsArr = groupByConfigsArr.findAll{it.Active == true}.RowKey.collect { it.toUpperCase().split(DBIFS) }.transpose()
    // println activeGroupByKeysArrsArr

    ArrayList sortKeysArr = sqlParamUserInputValues.findAll { it.isSorted }?.Value[0]?.split(/\s*,\s*/)
    // println sortKeysArr
    // println ""

    // Virtual Columns
    // println sqlColumnNamesArr
    def virtualColumnsMap = [:]
    if (virtualColumns) {
        virtualColumns.each { vcConfig ->
            // println prettyJson(vcConfig)
            def vcValue = vcConfig.VirtualColumnRows?.find {it.TableInstanceId == tableInstanceId}?.Value
            // println vcValue
            int columnToInsertAfterIndex = sqlColumnNamesArr.indexOf(vcConfig.ColumnToInsertAfter) + 1
            // println columnToInsertAfterIndex
            if (vcValue) {
                virtualColumnsMap[columnToInsertAfterIndex] = vcValue
            }
            // Add VirtualColumn Label to column names
            sqlColumnNamesArr.add(columnToInsertAfterIndex, vcConfig.ColumnLabel)
        }
    }
    // println virtualColumnsMap
    // println sqlColumnNamesArr


    def reader = new BufferedReader(new InputStreamReader(is))

    def dataMap = [:]
    def lineIndex = 0
    while ((line = reader.readLine()) != null ) {

        def lineArr = line
            .replaceFirst(/$IFS\s*$/, "${IFS}LAST_ELEMENT_IS_BLANK")
            .split(/\s*$IFS\s*/)
            .collect{ it == "LAST_ELEMENT_IS_BLANK" ? "" : it }
        // println lineArr

        // Add VirtualColumn value to lineArr
        virtualColumnsMap.each { columnToInsertAfterIndex, vcValue ->
            // println columnToInsertAfterIndex
            lineArr.add(columnToInsertAfterIndex, vcValue)
        }
        // println lineArr.size()

        // Create the Data Table Location
        def dataTableLocation = sectionNumber + "-" + tableInstanceIndex.toString()

        // Add sqlParamValues to be parsed and included in an id.
        // This id will become the value of the attribute "link" on the td for this cell
        // which is a reference to the tableGroup with the same id
        lineArr += dataTableLocation + "###" + sqlParamValues

        if (sortKeysArr) {
            // find the item in the sortKeysArr which exists in this line make it the key for the line
            // the value will be an array of lines in case you had multiple lines for one key
            lineArr.collect {
                if (it in sortKeysArr) {
                    if (dataMap[it]) dataMap[it] << lineArr
                    else dataMap[it] = [lineArr]
                }
            }
        }
        else {
            lineArr.collect { it ->
                activeGroupByKeysArrsArr.each { activePivotKeysArr ->
                  if (it in activePivotKeysArr) {
                      dataMap[lineIndex] = [lineArr]
                  }
                }
                activePivotOnKeysArrsArr.each { activePivotKeysArr ->
                  if (it in activePivotKeysArr) {
                      dataMap[lineIndex] = [lineArr]
                  }
                }
            }

        }
        lineIndex++
    }
    // dataMap.each { println it}
    // println dataMap
    // println ""

    // sort rows
    if (sortKeysArr) {
        dataMap = dataMap.sort { sortKeysArr.indexOf(it.key) }
    }

    /* OUTPUT */
    def outData = new StringBuilder()
    dataMap.each { k, v ->
        v.each {
            outData.append(it.join(OFS) + NEWLINE)
        }
    }

    props.setProperty("document.dynamic.userdefined.ddp_sqlColumnNames", sqlColumnNamesArr.join(OFS))


    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);

}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
