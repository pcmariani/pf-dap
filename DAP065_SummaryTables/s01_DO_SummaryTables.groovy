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

    def sqlColumnsMapJson = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnsMap")
    // println sqlColumnsMapJson
    def sqlColumnsMap = sqlColumnsMapJson ? new JsonSlurper().parseText(sqlColumnsMapJson) : [:]
    // println sqlColumnsMap

    def keysLabelsMapJson = props.getProperty("document.dynamic.userdefined.ddp_pivotConfigsKeysLabelsMapJson")
    def keysLabelsMap = keysLabelsMapJson ? new JsonSlurper().parseText(keysLabelsMapJson) : []
    // println keysLabelsMap
    ArrayList keysLabelsMapKeySet = keysLabelsMap ? keysLabelsMap.keySet() : []
    // println keysLabelsMapKeySet

    def reader = new BufferedReader(new InputStreamReader(is))

    def dataMap = [:]
    int lineIndex = 0

    Boolean addLineOnlyIfInKeysLabels = false
    def columnInPivotConfigArr = [false]*sqlColumnNamesArr.size()
    def firstLine = true
    while ((line = reader.readLine()) != null ) {

        def lineArr = line
            .replaceFirst(/$IFS\s*$/, "${IFS}LAST_ELEMENT_IS_BLANK")
            .split(/\s*$IFS\s*/)
            .collect{ it == "LAST_ELEMENT_IS_BLANK" ? "" : it }
        // println lineArr

        // Create the Data Table Location
        def dataTableLocation = sectionNumber + "-" + tableInstanceIndex.toString()

        // Add sqlParamValues to be parsed and included in an id.
        // This id will become the value of the attribute "link" on the td for this cell
        // which is a reference to the tableGroup with the same id
        lineArr += dataTableLocation + "###" + sqlParamValues
        // println lineArr

        ArrayList newLineArr = []
        String lineKey

        lineArr.withIndex().collect { item, j ->
            if (firstLine) {
                def columnInPivotConfig
                // println sqlColumnsMap
                if (sqlColumnsMap) {
                  columnInPivotConfig = sqlParamUserInputValues.find{sqlColumnsMap[it.ParamName] == sqlColumnNamesArr[j]}?.PivotConfig
                } else {
                  columnInPivotConfig = sqlParamUserInputValues.find{it.ParamName == sqlColumnNamesArr[j]}?.PivotConfig
                }
                // println sqlColumnNamesArr[j]
                // println sqlParamUserInputValues.find{sqlColumnsMap[it.ParamName] == sqlColumnNamesArr[j]}?.PivotConfig
                // println j + " -- " + columnInPivotConfig + "---" + sqlColumnNamesArr[j] + " -- " + item


                if (columnInPivotConfig) {
                    columnInPivotConfigArr[j] = true
                    addLineOnlyIfInKeysLabels = true
                }
            }
            
            // println item in keysLabelsMapKeySet
            // if (columnInPivotConfigArr[j]) {
            //   println j + " " + addLineOnlyIfInKeysLabels + " " + (item in keysLabelsMapKeySet) + " " + item
            // }
            if (item in keysLabelsMapKeySet && columnInPivotConfigArr[j]) {
            // if (item in keysLabelsMapKeySet) {
                // println item
                lineKey = item
                newLineArr << keysLabelsMap[item]
            } else {
                newLineArr << item
            }

        }
        // println addLineOnlyIfInKeysLabels.toString() + " " + lineKey + " " + newLineArr

        if (addLineOnlyIfInKeysLabels && lineKey) {
            dataMap[lineKey] = newLineArr
        }
        else if (!addLineOnlyIfInKeysLabels) {
            dataMap[lineIndex] = lineArr
        }

        // println columnInPivotConfigArr
        firstLine = false
    }
    // dataMap.each { println it; println ""}

    // Add Virtual Columns
    if (virtualColumns) {
        vcColToInsertAfterCountMap = sqlColumnNamesArr.collectEntries{[(it):0]}
        // println vcColToInsertAfterCountMap
        virtualColumns.each { vc ->
            int columnToInsertAfterIndex = sqlColumnNamesArr.indexOf(vc.ColumnToInsertAfter) + 1 + vcColToInsertAfterCountMap[vc.ColumnToInsertAfter]
            // println columnToInsertAfterIndex
            dataMap.each { line ->
                vc.VirtualColumnRows.each { vcr ->
                    def tableIdentifierArr = vcr.TableIdentifier.split(/\s*;\s*/) as ArrayList
                    // println tableIdentifierArr
                    // println line.value
                    // println line.value.intersect(tableIdentifierArr)
                    // if (line.key in tableIdentifierArr) {
                    //     // println vc.ColumnToInsertAfter + ": " + vcr.Value
                    //     line.value.add(columnToInsertAfterIndex, vcr.Value)
                    // }
                    if (line.value.intersect(tableIdentifierArr)) {
                    //     println line
                    //     println tableIdentifierArr
                        line.value.add(columnToInsertAfterIndex, vcr.Value)
                    }
                }
                // println line.value
            }
            // // Add VirtualColumn Label to column names
            sqlColumnNamesArr.add(columnToInsertAfterIndex, vc.ColumnLabel)
            vcColToInsertAfterCountMap[vc.ColumnToInsertAfter]++
        }
    }
    // dataMap.each { println it}
    props.setProperty("document.dynamic.userdefined.ddp_sqlColumnNames", sqlColumnNamesArr.join(OFS))
    println sqlColumnNamesArr


    // sort rows
    if (keysLabelsMapKeySet) {
        dataMap = dataMap.sort{ m -> keysLabelsMapKeySet.indexOf(m.key) }
    }
    // dataMap.each { println it }

    /* OUTPUT */

    def outData = new StringBuilder()
    dataMap.each { k, v ->
        outData.append(v.join(OFS) + NEWLINE)
    }


    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);

}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
