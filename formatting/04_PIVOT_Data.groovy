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
def DBFS = "^^^"    // Database Field Separator

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    /* INPUTS */

    // --- for pivot --- //

    def columnNames = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnNames") ?: ""
    def columnNamesArr = columnNames ? columnNames.split(IFS).collect{it.toUpperCase()} : []
    // println columnNamesArr

    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated")
    // def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigs")
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



    /* PREP */

    def topLeftCornerOpt = sources.PivotTopLeftCornerOpt ?: "PIVOT ON"
    // println "#DEBUG topLeftCornerOpt: " + topLeftCornerOpt
    int dataColIndex = columnNamesArr.indexOf(sources.PivotDataColumn.toUpperCase())
    // println "#DEBUG dataColIndex: " + dataColIndex
    def groupByColsArr = sources.PivotGroupByColumns.each{ 
        it.put("Index", columnNamesArr.indexOf(it.Column.toUpperCase()))
    }
    def pivotOnColsArr = sources.PivotOnColumns.each{ 
        it.put("Index", columnNamesArr.indexOf(it.Column.toUpperCase()))
    }
    // println "#DEBUG groupByColsArr: " + prettyJson(groupByColsArr)
    // println "#DEBUG pivotOnColsArr: " + prettyJson(pivotOnColsArr)

    int numHeaderRows = pivotOnColsArr.size()
    int numHeaderCols = groupByColsArr.size()

    // 1. renumber indices so they are offset by headers rows/cols
    // 2. split the rowlabels into arrays
    def activeGroupByConfigsArr = groupByConfigsArr.findAll { it.Active == true }.eachWithIndex { item, r ->
        item.RowIndex = numHeaderRows + r
        item.RowLabels = item.RowLabels.split(IFS) as ArrayList
    }
    def activePivotedDataConfigsArr = pivotedDataConfigsArr.findAll { it.Active == true }.eachWithIndex { item, c ->
        item.ColumnIndex = numHeaderCols + c
        item.ColumnLabels = item.ColumnLabels.split(IFS) as ArrayList
    }
    // println "#DEBUG activeGroupByConfigsArr: " + prettyJson(activeGroupByConfigsArr)
    // println "#DEBUG activePivotedDataConfigsArr: " + prettyJson(activePivotedDataConfigsArr)

    int numDataRows = activeGroupByConfigsArr.size()
    int numDataCols = activePivotedDataConfigsArr.size()
    int numRows = numHeaderRows + numDataRows
    int numCols = numHeaderCols + numDataCols



    /* LOGIC */

    // --- initialize result arr --- //

    def pivotedDataArr = []
    for (r = 0; r < numRows; r++) {
        pivotedDataArr[r] = []
        for (c = 0; c < numCols; c++) {
            pivotedDataArr[r][c] = NO_TEST
        }
    }
    // println pivotedDataArr


    // --- pivot data --- //

    // populate data cells within result arr with datapoints
    // using previously constructed pivotOn and groupBy configs
    def reader = new BufferedReader(new InputStreamReader(is))
    while ((line = reader.readLine()) != null ) {

        // replace a blank last element, then split, then replace the replacement with an empty string
        def lineArr = line
            .replaceFirst(/$IFS\s*$/, "${IFS}LAST_ELEMENT_IS_BLANK")
            .split(/\s*$IFS\s*/)
            .collect{ it == "LAST_ELEMENT_IS_BLANK" ? "" : it }

        def dataPoint = lineArr[dataColIndex]
        // println dataPoint

        def groupByMapKey = upper(groupByColsArr.collect{ if (it.IsKeyColumn) lineArr[it.Index]; else ""}.join(DBFS))
        // println "#DEBUG groupByMapKey: " + prettyJson(groupByMapKey)

        def pivotOnMapKey = upper(pivotOnColsArr.collect{ if (it.IsKeyColumn) lineArr[it.Index]; else ""}.join(DBFS))
        // println "#DEBUG pivotOnMapKey: " + prettyJson(pivotOnMapKey)

        def rowIndex = activeGroupByConfigsArr.find{ it.RowKey == groupByMapKey}?.RowIndex
        def colIndex = activePivotedDataConfigsArr.find{ it.ColumnKey == pivotOnMapKey}?.ColumnIndex

        // println rowIndex + " : " + colIndex
        // println ([rowIndex, colIndex, groupByMapKey, pivotOnMapKey, dataPoint].join("  |  "))

        if (rowIndex && colIndex) {
            pivotedDataArr[rowIndex][colIndex] = dataPoint
        }
        // println pivotedDataArr[rowIndex]

    }
    // pivotedDataArr.each { println it }


    // --- Post processing --- //

    // 1. add header labels from configs
    // 2. determine if row/columns should be removed, because
    //    - there are no values for the row/column
    //    - the config for that row/column has SuppressIfNoDataForAll(Rows|Cols) = true
    //      (this is most common)
    def colsToRemove = []
    def rowsToRemove = []
    for (r = 0; r < numRows; r++) {

        // --- determine if row should be removed --- //

        // first data row only
        if (r >= numHeaderRows) {
            // println r + " " + pivotedDataArr[r].clone().unique() + " " + activeGroupByConfigsArr[r - numHeaderRows].SuppressIfNoDataForAllCols
            if (pivotedDataArr[r].clone().unique() == [NO_TEST]
                && activeGroupByConfigsArr[r - numHeaderRows].SuppressIfNoDataForAllCols) {
                println c + " " + activeGroupByConfigsArr[r - numHeaderRows].RowKey
                rowsToRemove << c
            }
            // suppressIfNoDataForAllCols = true
        }

        for (c = 0; c < numCols; c++) {
            // println "row " + r + " : col " + c + " | "

            // --- add header labels from configs --- //

            // top-left corner
            if (r < numHeaderRows && c < numHeaderCols) {
                if (topLeftCornerOpt =~ /(?i)group ?by/) {
                    pivotedDataArr[r][c] = (groupByColsArr[c].Label ?: groupByColsArr[c].Column)
                } else {
                    pivotedDataArr[r][c] = (pivotOnColsArr[r].Label ?: pivotOnColsArr[r].Column)
                }
            } 

            // headers accross top (to the right of the top-left corner)
            if (r < numHeaderRows && c >= numHeaderCols) {
                pivotedDataArr[r][c] = activePivotedDataConfigsArr[c - numHeaderCols].ColumnLabels[r]
            } 
            // headers down the left side (below the top-left corner)
            if (r >= numHeaderRows && c < numHeaderCols) {
                // println "row " + r + " : col " + c + " | "
                pivotedDataArr[r][c] = activeGroupByConfigsArr[r - numHeaderRows].RowLabels[c]
            }

            // --- determine if column should be removed --- //

            // first data row within data cells (below header rows and to the right of header cols)
            if (r == numHeaderRows && c >= numHeaderCols) {
                // print c + " " + pivotedDataArr.transpose()[c][numHeaderRows..-1].clone().unique()
                if (pivotedDataArr.transpose()[c][numHeaderRows..-1].clone().unique() == [NO_TEST]
                    && activePivotedDataConfigsArr[c - numHeaderCols]?.SuppressIfNoDataForAllRows) {
                    // println c + " " + activePivotedDataConfigsArr[c - numHeaderCols]?.ColumnIndex
                    colsToRemove << c
                }
            }

        }
    }
    // pivotedDataArr.each { println it }
    // println rowsToRemove
    // println colsToRemove

    def topLeftCornerKeysArr = pivotedDataArr[0..numHeaderRows-1].collect{ it[0..numHeaderCols-1] }
    props.setProperty("document.dynamic.userdefined.ddp_topLeftCornerKeysArrJson", prettyJson(topLeftCornerKeysArr))


    // --- Post Post processing --- //

    // remove rows/cols if necessary from data and config
    // data contains headers, so index is just r/c
    // configs do not contain headers, so index is r/c - numHeaderRows/Cols
    for (r = 0; r < numRows; r++) {
        if (r in rowsToRemove) {
            pivotedDataArr.removeAt(r)
            activeGroupByConfigsArr.removeAt(r - numHeaderRows)

        }
    }
    for (c = 0; c < numCols; c++) {
        if (c in colsToRemove) {
            for (r = 0; r < numRows; r++) {
                pivotedDataArr[r].removeAt(c)
            }
            activePivotedDataConfigsArr.removeAt(c - numHeaderCols)
        }
    }
    // pivotedDataArr.each { println it.size() }
    // println activePivotedDataConfigsArr.size()
    // activePivotedDataConfigsArr.each { println it }



    /* OUTPUT */

    // DBFS = '\t'
    def outData = new StringBuffer()
    pivotedDataArr.each {outData.append(it.join(DBFS) + NEWLINE)}

    props.setProperty("document.dynamic.userdefined.ddp_GroupByConfigsConsolidated", prettyJson(
        [Records:activeGroupByConfigsArr.collect{ it.subMap("RowKey","RowIndex") }]
    ))
    props.setProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated", prettyJson(
        [Records:activePivotedDataConfigsArr.collect{ it.subMap("ColumnKey","ColumnIndex","SubTableIndex","ColumnWidth") }]
    ))

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}


private static String upper(String str) { return str.replaceAll("\\P{Print}","").toUpperCase() }
private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
