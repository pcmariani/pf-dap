// This pivot has expectations of the incoming data that PivotOn, GroupBy,
// and Data columns will not be blank. Use a COALESCE function in the Sql

import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;
logger = ExecutionUtil.getBaseLogger()

def NEWLINE = System.lineSeparator()
def NO_RESULT = "NR"
def NO_TEST = "NT"
def IFS = /\|\^\|/  // Input Field Separator
def OFS = "|^|"     // Output Field Separator
// def OFS = "\t"     // Output Field Separator

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    /* INPUTS */

    Boolean transpose = (props.getProperty("document.dynamic.userdefined.ddp_transpose") ?: "false").toBoolean()
    // println transpose
    def columnNames = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnNames") ?: ""
    def columnNamesArr = columnNames ? columnNames.split(IFS).collect{it.toUpperCase()} : []
    // println columnNamesArr
    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated")
    def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson)?.Records : []
    // println prettyJson(pivotedDataConfigsArr)
    // println pivotedDataConfigsArr.size()
    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = new JsonSlurper().parseText(sourcesJson).Records[0]
    // println prettyJson(sources)
    def sqlParamValues = props.getProperty("document.dynamic.userdefined.ddp_sqlParamValues")
    // println sqlParamValues

    /* LOGIC */

    def pivotOnColsArr = sources.PivotOnColumns
        .each{ it.put("Index", columnNamesArr.indexOf(it.Column.toUpperCase())) }
    // println prettyJson(pivotOnColsArr)
    def groupByColsArr = sources.PivotGroupByColumns
        .each{ it.put("Index", columnNamesArr.indexOf(it.Column.toUpperCase())) }
    // println prettyJson(groupByColsArr)
    def dataCol = sources.PivotDataColumn.toUpperCase()
    // println dataCol
    int dataColIndex = columnNamesArr.indexOf(dataCol)
    // println dataColIndex

    /* --- 1st pass: loop through data -> pivotDataMap--- */

    def pivotDataMap = [:]
    def pivotOnKeySetMap_AllCols = [:]

    def reader = new BufferedReader(new InputStreamReader(is))
    while ((line = reader.readLine()) != null ) {
        // Split the line.
        // Unfortunately the length of the array is calcualted to the last element populated in the line.
        // So lines can have different lengths, even though the number of delimiters is the same.
        // We replace a blank last element, then split, then replace the replacement with an empty string
        def lineArr = line
            .replaceFirst(/$IFS\s*$/, "${IFS}LAST_ELEMENT_IS_BLANK")
            .split(/\s*$IFS\s*/)
            .collect{ it == "LAST_ELEMENT_IS_BLANK" ? "" : it }
        def groupByMapKey = groupByColsArr.collect { lineArr[it.Index] ?: "-" }.join("___")
        def pivotOnMapKey_AllCols = pivotOnColsArr.collect { lineArr[it.Index] ?: "-" }.join("___")
        def pivotOnMapKey_KeyCols = pivotOnColsArr.collect { if (it.IsKeyColumn) lineArr[it.Index]; else "REMOVE" }.join("___").replaceAll("___REMOVE","")
        def dataPoint = lineArr[dataColIndex]
        // populate map
        if (!pivotDataMap[groupByMapKey]) {
            pivotDataMap[groupByMapKey] = [:]
            pivotDataMap[groupByMapKey][upper(pivotOnMapKey_AllCols)] = dataPoint
        }
        else if (!pivotDataMap[groupByMapKey][upper(pivotOnMapKey_AllCols)]) {
            pivotDataMap[groupByMapKey][upper(pivotOnMapKey_AllCols)] = dataPoint
        }
        // println groupByMapKey + " ::: " + pivotOnMapKey_AllCols + " ::: " + pivotDataMap[groupByMapKey][upper(pivotOnMapKey_AllCols)]

        // populate keySets
        pivotOnKeySetMap_AllCols[upper(pivotOnMapKey_KeyCols)] = pivotOnMapKey_AllCols
    }
    // println pivotDataMap.keySet()
    // println prettyJson(pivotOnKeySetMap_AllCols)
    // println pivotOnKeySetMap_AllCols.size()
    // println prettyJson(pivotDataMap)

    // if any of the groupBy keys are in the sqlParamValues, that means that we can sort by the param values
    // this is a hacky way of identifying that there is an IN operator and is a multiselect
    // The better way would be to include the operator in the UserInputValues
    def keySetMatrix = pivotDataMap.keySet().collect { it.split("___") }
    // println keySetMatrix
    def sqlParamValuesArr = sqlParamValues.replaceAll(";",",").split(/\s*,\s*/).collect{it.toUpperCase()} as ArrayList
    // println sqlParamValuesArr
    def multiselectSortKeysArr = []
    sqlParamValuesArr.each { param ->
        keySetMatrix.collect { keyArr ->
            if (param in keyArr) {
                multiselectSortKeysArr << keyArr.join("___").toUpperCase()
            }
        }
    }
    // println multiselectSortKeysArr
    if (multiselectSortKeysArr) {
        pivotDataMap = pivotDataMap.sort { multiselectSortKeysArr.indexOf(it.key.toUpperCase()) }
    }
    // println pivotDataMap.keySet()
    // println prettyJson(pivotDataMap)


    /* --- get all active pivotedDataConfig records --- */

    // 1. Create a final list of columnKeys, columnKeysActive, which is all the columnKeys
    // from the pivotedDataConfigs which are active only.
    // 2. There are cases where there is one or more config items for which there
    // is no data. For each of these config itmes, if SuppressIfNoDataForAllRows = false,
    // create a columnKey and a dataPoint.
    // 3. Also, create a new array almost the same as columnKeysActive, but with the labels
    // instead of original names of the keys
    // 4. Create a new set of configs with the items removed if we don't want them. In,
    // this case, it's any configs where there was no data and where
    // SuppressIfNoDataForAllRows = false
    def columnKeysActive = []
    def columnLabelsActive = []
    def pivotedDataConfigsArrItemsRemoved = []
    pivotedDataConfigsArr.findAll { configItem -> configItem.Active == true }
        .each { activeConfigItem ->
            // set the columnKey if there is one in the data
            def columnKey = pivotOnKeySetMap_AllCols[activeConfigItem.ColumnKey]
            // if there is not a columnKey in the data AND SuppressIfNoDataForAllRows = false...
            if (!columnKey && !activeConfigItem.SuppressIfNoDataForAllRows) {
                def activeItemNotInDataLabelsArr = activeConfigItem.PivotedItemLabels.collect{it.Label}
                (0..pivotOnColsArr.size()-1).each { p ->
                    if (!activeItemNotInDataLabelsArr[p]) activeItemNotInDataLabelsArr << NO_TEST
                }
                // set a column key from the config item
                columnKey = activeItemNotInDataLabelsArr.join("___")
                // add dataPoint representing NO_TEST for each groupBy
                pivotDataMap.keySet().each { pivotDataMap[it][upper(columnKey)] = NO_TEST }
            }

            // there will be a column key if it exists in the config item AND it exists in
            // the data OR if doesn't exist in the data but it does exist in the config item
            // AND SuppressIfNoDataForAllRows = false
            if (columnKey) {
                columnKeysActive << columnKey
                // the config item only has labels for pivotOn column which are marked
                // as key columns. For any pivotOn columns which are not key columns,
                // use the original column name
                def labelsArr = []
                columnKey.split("___").eachWithIndex { columnName, j ->
                    def activeConfigItemLabelsArr = activeConfigItem.PivotedItemLabels.collect{ it.Label }
                    if (activeConfigItemLabelsArr[j] != null) {
                        labelsArr << activeConfigItemLabelsArr[j]
                    }
                    else labelsArr << columnName
                }
                columnLabelsActive << labelsArr.join("___")
                // add config item to new array
                pivotedDataConfigsArrItemsRemoved << activeConfigItem
            }
        }
    // println prettyJson(columnKeysActive)
    // println columnKeysActive.size()
    // println prettyJson(columnLabelsActive)
    // println columnLabelsActive.size()
    // println prettyJson(pivotedDataConfigsArrItemsRemoved)
    // println pivotedDataConfigsArrItemsRemoved.size()

    /* --- start building result --- */

    def topLeftCornerOpt = sources.PivotTopLeftCornerOpt ?: "PIVOT ON"
    // println topLeftCornerOpt

    // top left corner
    def topLeftCornerArr = topLeftCornerOpt =~ /(?i)group ?by/ ?
        (groupByColsArr.collect { it.Label ?: it.Column } * pivotOnColsArr.size()) :
        pivotOnColsArr.collect { it.Label ?: it.Column }
    // println topLeftCornerArr

    def topLeftCornerMatrix = topLeftCornerOpt =~ /(?i)group ?by/ ?
        ([topLeftCornerArr] * groupByColsArr.size()) :
        [topLeftCornerArr] * groupByColsArr.size()
    // println topLeftCornerMatrix


    // header rows
    def headerRowsArr = []
    headerRowsArr << topLeftCornerMatrix.collect{it.join("___")}
    headerRowsArr << columnLabelsActive
    // println columnLabelsActive
    // println headerRowsArr.flatten().collect{it.split("___")}

    // add header rows to result
    def resultArr = headerRowsArr.flatten().collect{it.split("___")}.transpose()
    // println resultArr

    /* --- 2nd pass: loop through pivotDataMap -> resultArr --- */

    pivotDataMap.keySet().each { groupByKey ->
        def pivotDataRowArr = []
        columnKeysActive.each { columnKey ->
            // println columnKey
            pivotDataRowArr << (pivotDataMap[groupByKey][upper(columnKey)] ?: NO_RESULT)
        }
        def dataRowsArr = []
        dataRowsArr << groupByKey.split("___") // groupBy cols
        dataRowsArr << pivotDataRowArr // data cols
        // println dataRowsArr
        // add data row to result
        resultArr << (dataRowsArr.flatten())
    }

    /* OUTPUT */

    if (transpose) resultArr = resultArr.transpose()

    def outData = new StringBuffer()
    resultArr.each {outData.append(it.join(OFS) + NEWLINE)}

    if (!pivotDataMap) props.setProperty("document.dynamic.userdefined.ddp_hasNoDbData", "true")
    props.setProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated", JsonOutput.toJson([Records: pivotedDataConfigsArrItemsRemoved]))

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}


private static String upper(String str) { return str.replaceAll("\\P{Print}","").toUpperCase() }
private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
