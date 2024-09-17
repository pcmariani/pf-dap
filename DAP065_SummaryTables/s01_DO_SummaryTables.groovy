// This script outputs one or more |^| delimited rows which will be later put
// together to form the summary table for all the data tables generated
// in the process execution
//
// When a source of resultTableType "Summary Table" goes into the step which
// executes the sql statements, the result is a row or rows of summary table
// data.
//
// The output of this script is similar to the input. The differences are:
//
// 1. A new column is added as the last column: the "Data Table Location"
//    - There are two parts to it:
//      1. a concatenation of the section number and the tableInstanceIndex
//         - example: 3.7.S.2.8-2   -- the tableInstanceIndex is 2
//      2. the sqlParamValues
//         - these are appended to the same data table location value
//           but delimted with a #
//         - these are used later in the formatting subprocess so that
//           a hyperlink can be created from the summary table row,
//           specifically this newly created column, and the data table
//           this row corresponds to. The sqlParamValues are unique key
//           that links them.
//
// 2. Virtual columns are added
//
// 3. The rows are sorted
//
// What makes this complicated is the rows have to be sorted by a key in
// the data row, but we don't know whick value is the key.
// The sorting is determined by the order of records in the pivotConfig

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

    /* INPUTS */

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

    // this was 
    def sqlColumnsMapJson = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnsMap")
    //println sqlColumnsMapJson
    def sqlColumnsMap = sqlColumnsMapJson ? new JsonSlurper().parseText(sqlColumnsMapJson) : [:]
    //println sqlColumnsMap

    // the following object was created in the previous script: SORT_MultiSelectParam
    // repeating the comment from that script:
    //   Create a map where the keys are the RowKey/ColumnKey and values are RowLabels/ColumnLabels
    //   It is formed from both activeGroupByConfigsArr and activePivotedDataConfigsArr
    //   Its purpose is to provide a quick reference for:
    //   - matching keys with labels
    //   - sorting - the list is taking from the configs in order
    def keysLabelsMapJson = props.getProperty("document.dynamic.userdefined.ddp_pivotConfigsKeysLabelsMapJson")
    def keysLabelsMap = keysLabelsMapJson ? new JsonSlurper().parseText(keysLabelsMapJson) : []
    // println keysLabelsMap
    ArrayList keysLabelsMapKeySet = keysLabelsMap ? keysLabelsMap.keySet() : []
    // println keysLabelsMapKeySet

    /* LOGIC */

    def reader = new BufferedReader(new InputStreamReader(is))
    int lineIndex = 0
    def firstItem = true
    Boolean addLineOnlyIfInKeysLabels = false
    def columnInPivotConfigArr = [false]*sqlColumnNamesArr.size()
    def dataMap = [:] // populated in while loop

    while ((line = reader.readLine()) != null ) {

        /*
         * dataTableLocation START
         * Adds a column at the end of the summary row record called dataTableLocation
         */

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

        /* 
         * END dataTableLocation
         */

        /*
         * sort summary rows - step 1 craete map START
         * Adds a column at the end of the summary row record called dataTableLocation
         */
        ArrayList newLineArr = []
        String lineKey

        lineArr.withIndex().collect { item, j ->
            // assumes that, if we are dealing with a multiSelectParam that we'll need to sort by,
            // there will only be one key in the first column
            if (firstItem) {
                def columnInPivotConfig = false
                //println sqlColumnsMap
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
            //if (columnInPivotConfigArr[j]) {
            //    println j + " " + addLineOnlyIfInKeysLabels + " " + (item in keysLabelsMapKeySet) + " " + item
            //}
            if (item in keysLabelsMapKeySet && columnInPivotConfigArr[j]) {
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

        /* 
         * END sort - step 1
         */

        // println columnInPivotConfigArr
        firstItem = false
    }
    // dataMap.each { println it; println ""}

    /*
     * Add Virtual Columns START
     * Adds a column at the end of the summary row record called dataTableLocation
     */

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
                    // println line.value.intersect(tableIdentifierArr)
                    if (line.value.intersect(tableIdentifierArr)) {
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
    // println sqlColumnNamesArr

    /* 
     * END add Virtual Columns
     */


    // sort rows - step 2 -> sort map keys by order in keysLabelsMapKeySet
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
