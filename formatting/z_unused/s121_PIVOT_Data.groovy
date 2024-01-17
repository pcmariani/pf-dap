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
    
    def groupByConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_GroupByConfigsConsolidated")
    def groupByConfigsArr = groupByConfigsJson ? new JsonSlurper().parseText(groupByConfigsJson)?.Records : []
    // println "#DEBUG ConfigsArr: " + prettyJson(groupByConfigsArr)
    // println groupByConfigsArr.size()

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
    def groupByKeySetMap_AllCols = [:]

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

        def groupByMapKey_AllCols = groupByColsArr.collect { lineArr[it.Index] ?: "-" }.join("___")
        def groupByMapKey_KeyCols = groupByColsArr.findAll{it.IsKeyColumn}.collect { lineArr[it.Index] }.join("___")
        // println prettyJson(groupByMapKey_AllCols_AllCols)
        // println prettyJson(groupByMapKey_KeyCols)

        def pivotOnMapKey_AllCols = pivotOnColsArr.collect { lineArr[it.Index] ?: "-" }.join("___")
        def pivotOnMapKey_KeyCols = pivotOnColsArr.findAll{it.IsKeyColumn}.collect { lineArr[it.Index] }.join("___")
        // println prettyJson(pivotOnMapKey_AllCols)
        // println prettyJson(pivotOnMapKey_KeyCols)

        def dataPoint = lineArr[dataColIndex]

        // populate map
        if (!pivotDataMap[groupByMapKey_AllCols]) {
            pivotDataMap[groupByMapKey_AllCols] = [:]
            pivotDataMap[groupByMapKey_AllCols][upper(pivotOnMapKey_AllCols)] = dataPoint
        }
        else if (!pivotDataMap[groupByMapKey_AllCols][upper(pivotOnMapKey_AllCols)]) {
            pivotDataMap[groupByMapKey_AllCols][upper(pivotOnMapKey_AllCols)] = dataPoint
        }
        println groupByMapKey_AllCols + " ::: " + pivotOnMapKey_AllCols + " ::: " + pivotDataMap[groupByMapKey_AllCols][upper(pivotOnMapKey_AllCols)]

        // populate keySets
        pivotOnKeySetMap_AllCols[upper(pivotOnMapKey_KeyCols)] = pivotOnMapKey_AllCols
        groupByKeySetMap_AllCols[upper(groupByMapKey_KeyCols)] = groupByMapKey_AllCols
    }
    // println prettyJson(pivotDataMap)
    // println pivotDataMap.keySet()
    // println prettyJson(pivotOnKeySetMap_AllCols)
    // println pivotOnKeySetMap_AllCols.size()
    // println prettyJson(groupByKeySetMap_AllCols)

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

                // def activeItemNotInDataLabelsArr = activeConfigItem.PivotedItemLabels.collect{it.Label} // OLD uses Labels Map
                def activeItemNotInDataLabelsArr = activeConfigItem.ColumnLabels.split("___")
                (0..pivotOnColsArr.size()-1).each { p ->
                    if (!activeItemNotInDataLabelsArr[p]) activeItemNotInDataLabelsArr << NO_TEST
                }
                // set a column key from the config item
                columnKey = activeItemNotInDataLabelsArr.join("___")

                // add dataPoint representing NO_TEST for each groupBy
                pivotDataMap.keySet().each { pivotDataMap[it][upper(columnKey)] = NO_TEST }
            }
            // println "\ncolumnKey: " + columnKey

            // there will be a column key if it exists in the config item AND it exists in
            // the data OR if doesn't exist in the data but it does exist in the config item
            // AND SuppressIfNoDataForAllRows = false
            if (columnKey) {
                columnKeysActive << columnKey

                // the config item only has labels for pivotOn column which are marked
                // as key columns. For any pivotOn columns which are not key columns,
                // use the original column name
                def labelsArr = []
                // println columnKey
                def columnKeysArr = columnKey.split("___")
                // println "#DEBUG columnKeysArr: " + columnKeysArr
                def activeConfigItemLabelsArr = activeConfigItem.ColumnLabels.split("___") as ArrayList
                // println "#DEBUG activeConfigItemLabelsArr: " + activeConfigItemLabelsArr

                int keyLabelsCounter = 0
                pivotOnColsArr.eachWithIndex { configItem, r ->
                    if (!configItem.IsKeyColumn) {
                        labelsArr << columnKeysArr[r]
                    }
                    else {
                        labelsArr << activeConfigItemLabelsArr[keyLabelsCounter]
                        keyLabelsCounter++
                    }
                    // println r + " " + configItem
                }
                // columnLabelsActive << labelsArr.join("___")
                columnLabelsActive << labelsArr
                // add config item to new array
                pivotedDataConfigsArrItemsRemoved << activeConfigItem
            }
        }
    // println prettyJson(columnKeysActive)
    // println "#DEBUG columnKeysActive: " + prettyJson(columnKeysActive)
    // println "#DEBUG columnLabelsActive: " + prettyJson(columnLabelsActive)
    // println columnKeysActive.size()
    // println columnLabelsActive.size()
    // println prettyJson(pivotedDataConfigsArrItemsRemoved)
    // println pivotedDataConfigsArrItemsRemoved.size()


    /* --- active rows --- */












    def rowKeysActive = []
    def rowLabelsActive = []
    def groupByConfigsArrItemsRemoved = []

    groupByConfigsArr.findAll { configItem -> configItem.Active == true }
        .each { activeConfigItem ->

            // set the rowKey if there is one in the data
            def rowKey = groupByKeySetMap_AllCols[activeConfigItem.RowKey]

            // if there is not a rowKey in the data AND SuppressIfNoDataForAllRows = false...
            if (!rowKey && !activeConfigItem.SuppressIfNoDataForAllCols) {

                // def activeItemNotInDataLabelsArr = activeConfigItem.PivotedItemLabels.collect{it.Label} // OLD uses Labels Map
                def activeItemNotInDataLabelsArr = activeConfigItem.rowLabels.split("___")
                (0..groupByColsArr.size()-1).each { p ->
                    if (!activeItemNotInDataLabelsArr[p]) activeItemNotInDataLabelsArr << NO_TEST
                }
                // set a row key from the config item
                rowKey = activeItemNotInDataLabelsArr.join("___")

                // add dataPoint representing NO_TEST for each groupBy
                // pivotDataMap.keySet().each { pivotDataMap[it][upper(rowKey)] = NO_TEST }
            }
            // println "\nrowKey: " + rowKey

            // there will be a row key if it exists in the config item AND it exists in
            // the data OR if doesn't exist in the data but it does exist in the config item
            // AND SuppressIfNoDataForAllCols = false
            if (rowKey) {
                rowKeysActive << rowKey

                // the config item only has labels for groupBy row which are marked
                // as key rows. For any groupBy rows which are not key rows,
                // use the original row name
                def labelsArr = []
                // println rowKey
                def rowKeysArr = rowKey.split("___")
                // println "#DEBUG rowKeysArr: " + rowKeysArr
                def activeConfigItemLabelsArr = activeConfigItem.RowLabels.split("___") as ArrayList
                // println "#DEBUG activeConfigItemLabelsArr: " + activeConfigItemLabelsArr

                int keyLabelsCounter = 0
                groupByColsArr.eachWithIndex { configItem, r ->
                    if (!configItem.IsKeyrow) {
                        labelsArr << rowKeysArr[r]
                    }
                    else {
                        labelsArr << activeConfigItemLabelsArr[keyLabelsCounter]
                        keyLabelsCounter++
                    }
                    // println r + " " + configItem
                }
                // rowLabelsActive << labelsArr.join("___")
                rowLabelsActive << labelsArr
                // add config item to new array
                groupByConfigsArrItemsRemoved << activeConfigItem
            }
        }
        // println "#DEBUG rowKeysActive: " + prettyJson(rowKeysActive)
        // println "#DEBUG rowLabelsActive: " + prettyJson(rowLabelsActive)


















    /* --- start building result --- */

    // top Left corner

    def topLeftCornerOpt = sources.PivotTopLeftCornerOpt ?: "PIVOT ON"
    // println "#DEBUG topLeftCornerOpt: " + topLeftCornerOpt

    def topLeftCornerKeysArr = []
    def headerRowsArr = []

    if (topLeftCornerOpt =~ /(?i)group ?by/) {
        topLeftCornerKeysArr = [groupByColsArr.collect { it.Label ?: it.Column }] * pivotOnColsArr.size()
        headerRowsArr = topLeftCornerKeysArr.withIndex().collect { item, b -> item + columnLabelsActive.transpose()[b] }
    } else {
        topLeftCornerKeysArr = pivotOnColsArr.collect { [it.Label ?: it.Column] * groupByColsArr.size() }
        headerRowsArr = topLeftCornerKeysArr.withIndex().collect { item, b -> item + columnLabelsActive.transpose()[b] }
    }
    // println "#DEBUG topLeftCornerKeysArr (pivotOn): " + prettyJson(topLeftCornerKeysArr)
    // println "#DEBUG headerRowsArr: " + prettyJson(headerRowsArr)

    props.setProperty("document.dynamic.userdefined.ddp_topLeftCornerKeysArrJson", prettyJson(topLeftCornerKeysArr))

    def resultArr = headerRowsArr
    // println "#DEBUG resultArr: " + prettyJson(resultArr)

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

    // if (transpose) resultArr = resultArr.transpose()

    def outData = new StringBuffer()
    resultArr.each {outData.append(it.join(OFS) + NEWLINE)}

    if (!pivotDataMap) props.setProperty("document.dynamic.userdefined.ddp_hasNoDbData", "true")
    props.setProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated", JsonOutput.toJson([Records: pivotedDataConfigsArrItemsRemoved]))

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}


private static String upper(String str) { return str.replaceAll("\\P{Print}","").toUpperCase() }
private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
