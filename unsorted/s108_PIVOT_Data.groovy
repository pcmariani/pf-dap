// This pivot has expectations of the incoming data
// - PivotOn, GroupBy, and Data columns will not be blank. Use a COALESCE function in the Sql
// This script should be broken up into two
// 1. Output is the pivotDataMap and ddp_NewPivotedDataConfigs
// 2. Output is the pivoted data

import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;
logger = ExecutionUtil.getBaseLogger()

def NEWLINE = System.lineSeparator()
def IFS = /\|\^\|/  // Input Field Separator
def OFS = "|^|"     // Output Field Separator
// def OFS = "\t"     // Output Field Separator

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    /* INPUTS */

    def requestor = props.getProperty("document.dynamic.userdefined.ddp_Requestor")
    def reportContentItem_DynamicTableId = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem_DynamicTableId")
    def tableDefinitionId = props.getProperty("document.dynamic.userdefined.ddp_TableDefinitionId")
    def sourceSqlQueryId = props.getProperty("document.dynamic.userdefined.ddp_SourceSqlQueryId")

    Boolean transpose = (props.getProperty("document.dynamic.userdefined.ddp_transpose") ?: "false").toBoolean()
    // println transpose
    ArrayList columnNamesArr = []
    def columnNames = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnNames") ?: ""
    if (columnNames) columnNamesArr = columnNames.split(IFS).collect{it.toUpperCase()}
    // println columnNamesArr
    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    // println sourcesJson
    def sources = new JsonSlurper().parseText(sourcesJson).Records[0]
    // println prettyJson(sources)
    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigs")
    def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson).Records : []
    // println prettyJson(pivotedDataConfigsArr)
    // pivotedDataConfigsArr.ColumnKey.each { println it }
    // println pivotedDataConfigsArr.size()

    /* --- set vars --- */
    def NO_RESULT = "NR"

    def pivotOnColsArr = sources.PivotOnColumns
        .each{ it.put("Index", columnNamesArr.indexOf(it.Column.toUpperCase())) }
    // println prettyJson(pivotOnColsArr)
    def groupByColsArr = sources.PivotGroupByColumns
        .each{ it.put("Index", columnNamesArr.indexOf(it.Column.toUpperCase())) }
    // println groupByColsArr
    def dataCol = sources.PivotDataColumn.toUpperCase()
    // println dataCol
    int dataColIndex = columnNamesArr.indexOf(dataCol)
    // println dataColIndex
    def topLeftCornerOpt = sources.PivotTopLeftCornerOpt ?: "PIVOT ON"
    // println topLeftCornerOpt

    /* LOGIC */

    /* --- 1st pass: loop through data -> pivotDataMap--- */

    def pivotDataMap = [:]
    def pivotedDataConfigKeyMap = [:]
    def pivotedDataConfigKeyMapKeyColsOnly = [:]
    Set pivotOnKeySet = []
    Set groupByKeySet = []

    def reader = new BufferedReader(new InputStreamReader(is))
    while ((line = reader.readLine()) != null ) {
        def lineArr = line.split(/\s*$IFS\s*/)
        def groupByMapKey = groupByColsArr.collect { lineArr[it.Index] ?: "-" }.join("___")
        def pivotOnMapKey = pivotOnColsArr.collect { lineArr[it.Index] ?: "-" }.join("___")
        def dataPoint = lineArr[dataColIndex]
        // populate map
        if (!pivotDataMap[groupByMapKey]) {
            pivotDataMap[groupByMapKey] = [:]
            pivotDataMap[groupByMapKey][upper(pivotOnMapKey)] = dataPoint
        }
        else if (!pivotDataMap[groupByMapKey][upper(pivotOnMapKey)]) {
            pivotDataMap[groupByMapKey][upper(pivotOnMapKey)] = dataPoint
        }
        // println groupByMapKey + " ::: " + pivotOnMapKey + " ::: " + pivotDataMap[groupByMapKey][upper(pivotOnMapKey)]

        // populate keySets
        // pivotOnKeySet.add(pivotOnMapKey)
        groupByKeySet.add(groupByMapKey)

        // populate pivotedDataConfigKeyMap with pivotOnKeys ONLY if they have IsKeyColumn=true
        // This way the pivot columns can have a unique superset for all tables
        def pivotedDataConfigKey = pivotOnColsArr.collect { if (it.IsKeyColumn) lineArr[it.Index]; else "REMOVE" }
                .join("___")
                .replaceAll("___REMOVE","")
        // println pivotedDataConfigKey

        pivotOnKeySet.add(pivotedDataConfigKey)
        // pivotOnKeySet.add(pivotOnMapKey)
        pivotedDataConfigKeyMap[upper(pivotedDataConfigKey)] = pivotOnMapKey
        pivotedDataConfigKeyMapKeyColsOnly[upper(pivotedDataConfigKey)] = pivotedDataConfigKey
    }
    // pivotOnKeySet.each { println it }
    // println pivotOnKeySet.size()
    // pivotedDataConfigKeyMap.each { println it }
    // pivotDataMap.each {println it}
    println prettyJson(pivotDataMap)
    println prettyJson(pivotedDataConfigKeyMap)
    // println prettyJson(pivotOnKeySet)
    // logger.warning("pivotDataMap: " + pivotDataMap.toString())
    // logger.warning("pivotOnKeySet: " + pivotOnKeySet.toString())
    // logger.warning("groupByKeySet: " + groupByKeySet.toString())

    /* --- conditionally create new pivotedDataConfig records --- */

    def columnKeyCounter = pivotedDataConfigsArr.ColumnKey.size() + 1
    def newPivotedDataConfigsArr = []
    // collect all pivot keys which are in the data but not in pivotedDataConfig records;
    // for each one, create a new pivotedDataConfig record
    pivotedDataConfigKeyMap.findAll { k,v ->
        !pivotedDataConfigsArr.ColumnKey.collect{upper(it)}.contains(upper(k))
    }.each{ key, value ->
        // println key + " ::: " + value
        newPivotedDataConfigsArr << [
            ColumnKey: key,
            Active: true,
            ColumnIndex: columnKeyCounter++,
            SubTableIndex: pivotedDataConfigsArr.SubTableIndex.max() ?: 1,
            ColumnWidth: 10,
            SuppressIfNoDataForAllRows: true,
            PivotedItemLabels: pivotedDataConfigKeyMapKeyColsOnly[key].split("___").collect{[Name:it, Label:it]}
        ]
    }
    // println prettyJson(newPivotedDataConfigsArr)

    if (newPivotedDataConfigsArr) {
        def newPivotedDataConfigsJson = JsonOutput.toJson([
            Requestor: requestor,
            ReportContentItem_DynamicTableId: reportContentItem_DynamicTableId,
            TableDefinitionId: tableDefinitionId,
            SourceId: sourceSqlQueryId,
            Records: newPivotedDataConfigsArr
        ])
        props.setProperty("document.dynamic.userdefined.ddp_NewPivotedDataConfigs", newPivotedDataConfigsJson)
    }
    println prettyJson(newPivotedDataConfigsArr)
    // println JsonOutput.toJson(pivotOnKeySet) //.collect { it.split("___") })
    // println pivotOnKeySet.collect { it.split("___") }.collect { it.collect { [Name:it, Label:it] }}









    /* --- get all active pivotedDataConfig records --- */

    // the final list of column keys off of which to base our pivot columns is made up of
    // those which are in the original pivotedDataConfigs which are active, plus any new ones
    def pivotedDataConfigsActive = (pivotedDataConfigsArr + newPivotedDataConfigsArr).findAll { it.Active == true }
    // println prettyJson(pivotedDataConfigsActive)
    // println ""
    // pivotedDataConfigsActive.ColumnKey.each { println it }
    // println pivotedDataConfigsActive.size()

    // How about this little incomprehensible gem
    // What if you have a column in the config but isn't in the data?
    // if so, create it:
    //   1. columnKey - create a columnKey for it which gets added
    //      to columnKeysActive (like all the ones that do exist here in the data)
    //   2. columnLabels - add the labels to pivotDataConfigKeyMap
    //   3. data - add it to the actual pivotDataMap
    def columnKeysActive = pivotedDataConfigsActive.collect {
        def columnKey = pivotedDataConfigKeyMap[upper(it.ColumnKey)]
        if (!columnKey) {
            def activeItemNotInDataLabelsArr = it.PivotedItemLabels.collect{it.Label}
            (0..pivotOnColsArr.size()-1).each { p ->
                if (!activeItemNotInDataLabelsArr[p]) activeItemNotInDataLabelsArr << "---"
            }
            columnKey = activeItemNotInDataLabelsArr.join("___")
            pivotedDataConfigKeyMap[it.ColumnKey] = activeItemNotInDataLabelsArr.join("___")
            groupByKeySet.each {pivotDataMap[it][upper(columnKey)] = "---"}
        }
        columnKey
    }
    // println prettyJson(columnKeysActive)
    // println columnKeysActive.size()

    def columnLabelsActive = []
    pivotedDataConfigsActive.collect{
        def labelsArr = []
        def activeItem = pivotedDataConfigKeyMap[upper(it.ColumnKey)]
        activeItem.split("___").eachWithIndex { item, j ->
            // println j + " :: " + it.ColumnKey + " :: " + item + " :: " + it.PivotedItemLabels.collect{it.Label}[j]
            if (it.PivotedItemLabels.collect{ it.Label }[j] != null) {
                labelsArr << it.PivotedItemLabels.collect{ it.Label }[j]
            }
            else labelsArr << item
        }
        columnLabelsActive << labelsArr.join("___")
    }
    // println prettyJson(columnLabelsActive)

    /* --- start building result --- */

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

    groupByKeySet.each { groupByKey ->
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

    int numHeaderRows = !transpose ? pivotOnColsArr.size() : groupByColsArr.size()
    props.setProperty("document.dynamic.userdefined.ddp_numHeaderRows", numHeaderRows.toString())
    int numGroupByCols = !transpose ? groupByColsArr.size() : pivotOnColsArr.size()
    props.setProperty("document.dynamic.userdefined.ddp_numGroupByCols", numGroupByCols.toString())
    def groupByKeySetJson = JsonOutput.toJson(groupByKeySet.collect { it.split("___") })
    props.setProperty("document.dynamic.userdefined.ddp_groupByKeySetJson", groupByKeySetJson)
    def pivotOnKeySetJson = JsonOutput.toJson(columnKeysActive.collect { it.split("___") })
    props.setProperty("document.dynamic.userdefined.ddp_pivotOnKeySetJson", pivotOnKeySetJson)

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}


private static String upper(String str) { return str.replaceAll("\\P{Print}","").toUpperCase() }
private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }


/* --- separators for fun (but no profit) --- */

// def showSeparator = false
// def verticalSeparator = transpose ? "------------------" : "|"
// def horizontalSeparator = transpose ? "|" : "-------------------"

// if (showSeparator) headerRowsArr << ([verticalSeparator] * groupByColsArr.size()).join("___")
// if (showSeparator) resultArr << [horizontalSeparator] * (groupByColsArr.size() + pivotOnKeySet.size() + 1)
// if (showSeparator) dataRowsArr << verticalSeparator
