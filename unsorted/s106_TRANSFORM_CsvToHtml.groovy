import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import groovy.xml.StreamingMarkupBuilder;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()
def IFS = /\|\^\|/  // Input Field Separater

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigs")
    def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson).Records : []
    // println prettyJson(pivotedDataConfigsArr)
    // println pivotedDataConfigsArr.size()

    // def newPivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_NewPivotedDataConfigs")
    // def newPivotedDataConfigsArr = newPivotedDataConfigsJson ? new JsonSlurper().parseText(newPivotedDataConfigsJson).Records : []
    // println prettyJson(newPivotedDataConfigsArr)
    // println newPivotedDataConfigsArr.size()
    //
    def reportContentItemJson = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem")
    def rowHeaderConfigArr = new JsonSlurper().parseText(reportContentItemJson).Records[0].RowHeaderConfig
    // println prettyJson(rowHeaderConfigArr)
    // println rowHeaderConfigArr.size()
    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = new JsonSlurper().parseText(sourcesJson).Records[0]
    // println prettyJson(sources)
    def groupByColsArr = sources.PivotGroupByColumns
    // println groupByColsArr
    def pivotOnColsArr = sources.PivotOnColumns
    // println pivotOnColsArr


    int tableDefinitionId = (props.getProperty("document.dynamic.userdefined.ddp_TableDefinitionId") ?: "1") as int
    // println tableDefinitionId
    int reportContentItemId = (props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem_DynamicTableId") ?: "1") as int
    // println reportContentItemId
    def sqlColumnNames = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnNames")
    def sqlColumnNamesArr = sqlColumnNames.split(/\s*$IFS\s*/)
    // println sqlColumnNamesArr
    Boolean isPivot = (props.getProperty("document.dynamic.userdefined.ddp_isPivot") ?: "true").toBoolean()
    // println isPivot
    Boolean displayHeaders = (props.getProperty("document.dynamic.userdefined.ddp_displayHeaders") ?: "true").toBoolean()
    // println displayHeaders
    Boolean transpose = (props.getProperty("document.dynamic.userdefined.ddp_transpose") ?: "false").toBoolean()
    // println transpose
    def resultTableType = props.getProperty("document.dynamic.userdefined.ddp_resultTableType") ?: "data"
    if (resultTableType =~ /(?i)Summary/) resultTableType = "summary"
    // println resultTableType

    // int numPivotOnCols = (props.getProperty("document.dynamic.userdefined.ddp_numPivotOnCols") ?: "1") as int
    // int numGroupByCols = (props.getProperty("document.dynamic.userdefined.ddp_numGroupByCols") ?: "1") as int
    // int numHeaderRows = (props.getProperty("document.dynamic.userdefined.ddp_numHeaderRows") ?: "1") as int
    // int numHeaderCols = (props.getProperty("document.dynamic.userdefined.ddp_numHeaderCols") ?: "1") as int
    // int numKeyHeaders = (props.getProperty("document.dynamic.userdefined.ddp_numKeyHeaders") ?: "1") as int

    int numHeaderRows = (props.getProperty("document.dynamic.userdefined.ddp_numHeaderRows") ?: "1") as int
    int numGroupByCols = (props.getProperty("document.dynamic.userdefined.ddp_numGroupByCols") ?: "1") as int
    // println "numHeaderRows: " + numHeaderRows
    // println "numGroupByCols: " + numGroupByCols
    if (!isPivot) numGroupByCols = sqlColumnNamesArr.size()
    def tableTitleText = props.getProperty("document.dynamic.userdefined.ddp_tableTitleText")
    // println tableTitleText
    def sqlParamValues = props.getProperty("document.dynamic.userdefined.ddp_sqlParamValues")
    // println sqlParamValues

    def numKeyHeaders = pivotOnColsArr.findAll{it.IsKeyColumn == true}.size()
    // println numKeyHeaders
    props.setProperty("document.dynamic.userdefined.ddp_numKeyRows", numKeyHeaders.toString())


    /*
     * PROBLEM: Creating sub-tables where the RowHeader columns repeat for each sub-table
     *
     * NOTES:
     *   - A sub-table here means a table in the HTML
     *   - We're calling the y-axis headers columns RowHeader columns:
     *     - The y-axis header columns for a non-transposed pivoted table are the GroupBy columns
     *     - The y-axis header columns for a transposed pivoted table are the PivotOn columns
     *
     * STEP 1: Make a columnsConfigArr that exactly matches the columns in the data.
     *   a. The columns in the data have one set of RowHeader columns + the data columns
     *   b. Create a pivtoedDataConfig object for each RowHeader column and place it at
     *      the begining of the columnsConfigArr. Then Add the pivotedDataConfigsActive objects.
     *   -  Now the number of items in the columnsConfigArr should match exactly the number of
     *      columns in the data.
     *
     * STEP 2: Loop through the columnsConfigArr and where ever the SubTableIndex increments,
     *   a. insert the newly created RewHeader column objects into the columnsConfigArr
     *   b. insert the RowHeader column data into the data array
     *
     */

    def pivotedDataConfigsActive = (pivotedDataConfigsArr + newPivotedDataConfigsArr).findAll { it.Active == true }
    // println prettyJson(pivotedDataConfigsActive)
    // println pivotedDataConfigsActive.size()

    def dataArr = []
    def reader = new BufferedReader(new InputStreamReader(is))
    while ((line = reader.readLine()) != null ) {
        dataArr << line.split(/\s*$IFS\s*/)
    }
    // dataArr.each { println it }
    // println dataArr[0].size()

    // int numSubTables = 1
    // if (isPivot && !transpose) numSubTables = pivotedDataConfigsActive.SubTableIndex.max()
    def numSubTables = (isPivot && !transpose) ? pivotedDataConfigsActive.SubTableIndex.max() : 1
    // println numSubTables

    // # NON-PIVOTED TRANSPOSED

    // # NON-PIVOTED NON-TRANSPOSED
    //   - need to construct columnsConfigArr completely

    // # PIVOTED NON-TRANSPOSED
    //   - columnsConfigArr =
    //      - rowHeaderConfigArr + pivotedDataConfigsActiveArr
    //   - rowHeaderConfigArr gets inserted when the subTableIndex increments

    // # PIVOTED TRANSPOSED
    //   - columnsConfigArr = rowHeaderConfigArr = constructed records
    //   - pivotedDataConfigsActiveArr becomes the rowHeaderKeys arr in the rowHeaderConfigArr

    /* --- RowHeaderKeys --- */

    def rowHeaderKeysArr = []
    // for trasnposed - construct a list of keys from the data for
    //   - just key rows
    //   - just groupBy cols
    if (isPivot && transpose) {
        def topLeftCornerKeys = dataArr.collect{it[0..numKeyHeaders-1].join("___")}[0..numGroupByCols-1]
        rowHeaderKeysArr = (topLeftCornerKeys + pivotedDataConfigsActive.ColumnKey).collect{it.split("___")}
    }
    // For non-transposed - construct a list of keys from the data for
    //   - all rows
    //   - just groupBy cols
    // for a non-pivot the number of groupBy cols will be the number of cols in the
    //   sqlColumnNames arr, which is usually all of them
    else rowHeaderKeysArr = dataArr.collect{it[0..numGroupByCols-1]}
    // println rowHeaderKeysArr


    // --- Row Header Columns Config --- //

    // for pivots - get from rowHeaderConfigArr OR construct if doesn't exist
    //   - the Column Keys doen't exist in the rowHeaderConfigArr so it is constructed from
    //     the data
    //   - for non-transposed - the row headers columns are the gropuBy columns
    //   - for transposed - the row header columns are the pivotOn columns

    // STEP 1a.
    def rowHeaderColumnConfigsArr = []
    if (isPivot) {
        if (!transpose) {
            groupByColsArr.eachWithIndex { groupByCol, c ->

                def columnKey = dataArr.transpose()[c][0..numKeyHeaders-1]
                def rowHeaderConfigItem = rowHeaderConfigArr.find{it.RowHeaderName == groupByCol.Column}
                rowHeaderColumnConfigsArr << [
                    ColumnName: rowHeaderConfigItem?.RowHeaderName ?: groupByCol.Column,
                    ColumnKey: columnKey.join("___"),
                    Active: true,
                    SubTableIndex: 1,
                    ColumnWidth: rowHeaderConfigItem?.RowHeaderWidth ?: 10,
                    RowHeaderKeys: rowHeaderKeysArr.collect{it.join("___")}
                ]
            }
        }
        else {
            pivotOnColsArr.eachWithIndex { pivotOnCol, c ->

                def columnKey = dataArr.transpose()[c][0..numGroupByCols-1]
                def rowHeaderConfigItem = rowHeaderConfigArr.find{it.RowHeaderName == pivotOnCol.Column}
                rowHeaderColumnConfigsArr << [
                    ColumnName: rowHeaderConfigItem?.RowHeaderName ?: pivotOnCol.Column,
                    ColumnKey: columnKey.join("___"),
                    Active: true,
                    SubTableIndex: 1,
                    ColumnWidth: rowHeaderConfigItem?.RowHeaderWidth ?: 10,
                    RowHeaderKeys: rowHeaderKeysArr.collect{it.join("___")}
                ]
            }
        }
    }
    // println prettyJson(rowHeaderColumnConfigsArr)

    /* --- Columns Config Arr --- */

    // for non-transposed pivot
    //   - prepend pivotedDataConfigsActive with rowHeaderColumnConfigsArr items
    //   - MAYBE SHOULD CREATE NEW ARR AND APPEND
    // for transposed pivot
    //   - create config records for data columns and append to rowHeaderColumnConfigsArr

    // STEP 1b.
    if (isPivot && !transpose) {
        rowHeaderColumnConfigsArr.reverse().each {
            pivotedDataConfigsActive.add(0,it)
        }
    }
    // println prettyJson(pivotedDataConfigsActive)
    // println pivotedDataConfigsActive.size()


    def columnsConfigArr = []
    def newDataArr = []

    if (isPivot && !transpose) {
        def newDataArrTransposed = []
        def subTableIndexPrev = 1
        pivotedDataConfigsActive.eachWithIndex { el, j ->
            if (el.SubTableIndex != subTableIndexPrev) {
                rowHeaderColumnConfigsArr.each {
                    def copy = it.clone()
                    copy.SubTableIndex = el.SubTableIndex
                    columnsConfigArr << copy
                }
                rowHeaderKeysArr.transpose().each {
                    newDataArrTransposed << it
                }
            }
            newDataArrTransposed << dataArr.transpose()[j]
            subTableIndexPrev = el.SubTableIndex
            columnsConfigArr << el
            // println newDataArrTransposed
        }
        newDataArr = newDataArrTransposed.transpose()
    }
    else if (isPivot && transpose) {
        // for transposed pivot - construct a list of key for the data columns
        columnsConfigArr = rowHeaderColumnConfigsArr
        def dataColumnKeys = dataArr[0..numGroupByCols-1].transpose()[numKeyHeaders..-1].collect{it.join("___")}
        // println dataColumnKeys
        dataColumnKeys.each { columnKey ->
            columnsConfigArr << [
                ColumnKey: columnKey,
                Active: true,
                SubTableIndex: 1,
                ColumnWidth: 10,
            ]
        }
        newDataArr = dataArr
    }
    else if (!isPivot) {
        dataArr[0].each { header ->
            columnsConfigArr << [
                ColumnKey: header,
                Active: true,
                SubTableIndex: 1,
                ColumnWidth: 10,
                RowHeaderKeys: rowHeaderKeysArr.collect{it.join("___")}
            ]
        }
        newDataArr = dataArr
    }
    // newDataArr.each {println it}
    // println prettyJson(columnsConfigArr)
    // println columnsConfigArr.size()
    // println columnsConfigArr.SubTableIndex
    // println newDataArr[0].size()


    def idDelim = "-----"

    def xmlMarkup = new StreamingMarkupBuilder().bind {
        // tableGroup
        def tableGroupId = reportContentItemId + idDelim + tableDefinitionId
        if (resultTableType != "summary") tableGroupId += idDelim + sqlParamValues

        'tablegroup'('Reference':"ref", 'id':"id"+md5(tableGroupId)) {  // <tablegroup> has a lowercase g
            'h3'('tableHeader':"yes", tableTitleText)
            (1..numSubTables).each { subTableIndex ->
                // table
                def tableId = tableGroupId + subTableIndex
                'table'('class':"output-table", 'border':1, 'width':"100%", 'pagebreak':"true", 'id':"id"+md5(tableId), 'parent_tablegroup':"id"+md5(tableGroupId), seq:subTableIndex) {
                    newDataArr.eachWithIndex{ rowArr, r ->
                        // tr
                        'tr'() {
                            rowArr.eachWithIndex { item, c ->
                                if (subTableIndex == columnsConfigArr[c].SubTableIndex) {
                                    // td
                                    def itemId = tableId + idDelim + columnsConfigArr[0].RowHeaderKeys[r] + idDelim + columnsConfigArr[c].ColumnKey + ( r < numKeyHeaders ? idDelim + item : "" )
                                    // if (r==2) { println subTableIndex + " :: " + columnsConfigArr[c].ColumnWidth + " :: " + itemId }

                                    // tableGroup link for Summary tables
                                    def summaryTableGroupLinkId
                                    if (resultTableType == "summary" && columnsConfigArr[c].ColumnKey == "Data Table Location") {
                                        ArrayList dataTableLocationItemArr = item.split("###", 2)
                                        item = dataTableLocationItemArr[0]
                                        def tableGroupSqlParams = dataTableLocationItemArr[1]
                                        summaryTableGroupLinkId = tableGroupId + idDelim + tableGroupSqlParams
                                        // println item + " :: " + columnsConfigArr[c].ColumnKey + " :: " + summaryTableGroupLinkId
                                    }

                                    def columnWidth =  columnsConfigArr[c].ColumnWidth

                                    if (r == 0) {
                                        'th'('columnWidth':columnWidth, id:"id"+md5(itemId), item)
                                    }
                                    else if (r < numHeaderRows) {
                                        'th'(id:"id"+md5(itemId), item)
                                    }
                                    else if (r == numHeaderRows) {
                                        if (resultTableType == "summary" && columnsConfigArr[c].ColumnKey == "Data Table Location") {
                                            'td'('columnWidth':columnWidth, id:"id"+md5(itemId), link:"id"+md5(summaryTableGroupLinkId), item)
                                        }
                                        else {
                                            'td'('columnWidth':columnWidth, id:"id"+md5(itemId), item)
                                        }
                                    }
                                    else {
                                        if (resultTableType == "summary" && columnsConfigArr[c].ColumnKey == "Data Table Location") {
                                            'td'(id:"id"+md5(itemId), link:"id"+md5(summaryTableGroupLinkId), item)
                                        }
                                        else {
                                            'td'(id:"id"+md5(itemId), item)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                'br'()
            }
        }
    }

    if (rowHeaderColumnConfigsArr) {
        def rowHeaderConfig = []
        rowHeaderColumnConfigsArr.each {
            rowHeaderConfig << [
                RowHeaderName: it.ColumnName,
                RowHeaderWidth: it.ColumnWidth
            ]
        }
        props.setProperty("document.dynamic.userdefined.ddp_RowHeaderConfig", JsonOutput.toJson(rowHeaderConfig))
    }

    is = new ByteArrayInputStream(xmlMarkup.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) {
    return JsonOutput.prettyPrint(JsonOutput.toJson(thing))
}

private static String md5(String str) {
    return str//.md5()
}
