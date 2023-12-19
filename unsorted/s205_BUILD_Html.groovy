import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import groovy.xml.StreamingMarkupBuilder;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()
def IFS = /\|\^\|/  // Input Field Separater

// def pivotedDataConfigsJson = ExecutionUtil.getDynamicProcessProperty("DPP_PivotedDataConfigs")
 
for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);
 
    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated")
    // def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigs")
    def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson)?.Records : []
    // println prettyJson(pivotedDataConfigsArr)
    // println pivotedDataConfigsArr.size()
    def rowHeaderConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_RowHeaderConfig")
    // println rowHeaderConfigsJson
    def rowHeaderConfigArr = rowHeaderConfigsJson ? new JsonSlurper().parseText(rowHeaderConfigsJson)?.Records[0].RowHeaderConfig : []
    // println prettyJson(rowHeaderConfigArr)
    // println rowHeaderConfigArr.size()
    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = sourcesJson ? new JsonSlurper().parseText(sourcesJson).Records[0] : []
    // println prettyJson(sources)
    def groupByColsArr = sourcesJson ? sources.PivotGroupByColumns : []
    // println groupByColsArr
    def pivotOnColsArr = sourcesJson ? sources.PivotOnColumns : []
    // println pivotOnColsArr
 
 
    int tableDefinitionId = (props.getProperty("document.dynamic.userdefined.ddp_TableDefinitionId") ?: "1") as int
    // println tableDefinitionId
    int reportContentItemId = (props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem_DynamicTableId") ?: "1") as int
    // println reportContentItemId
    def sqlColumnNames = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnNames")
    ArrayList sqlColumnNamesArr = sqlColumnNames.split(/\s*$IFS\s*/)
    // println sqlColumnNamesArr
    Boolean isPivot = (props.getProperty("document.dynamic.userdefined.ddp_isPivot") ?: "true").toBoolean()
    // println isPivot
    Boolean displayHeaders = (props.getProperty("document.dynamic.userdefined.ddp_displayHeaders") ?: "true").toBoolean()
    // println "displayHeaders: " + displayHeaders
    Boolean displayHeadersOnSide = (props.getProperty("document.dynamic.userdefined.ddp_displayHeadersOnSide") ?: "true").toBoolean()
    // println "displayHeadersOnSide: " + displayHeadersOnSide
    Boolean transpose = (props.getProperty("document.dynamic.userdefined.ddp_transpose") ?: "false").toBoolean()
    // println transpose
    def resultTableType = props.getProperty("document.dynamic.userdefined.ddp_resultTableType") ?: "data"
    if (resultTableType =~ /(?i)Summary/) resultTableType = "summary"
    // println resultTableType
    def tableTitleText = props.getProperty("document.dynamic.userdefined.ddp_tableTitleText")
    // println tableTitleText
    int numHeaderRows = (props.getProperty("document.dynamic.userdefined.ddp_numHeaderRows") ?: "1") as int
    // println "numHeaderRows: " + numHeaderRows
    // println isPivot.getClass()
    int numKeyHeaders = (props.getProperty("document.dynamic.userdefined.ddp_numKeyHeaders") ?: "1") as int
    // println numKeyHeaders
    int numHeaderCols = isPivot ?
        (props.getProperty("document.dynamic.userdefined.ddp_numHeaderCols") ?: "1") as int :
        sqlColumnNamesArr.size()
    // println "numHeaderCols: " + numHeaderCols
    def sqlParamValues = props.getProperty("document.dynamic.userdefined.ddp_sqlParamValues")
    // println sqlParamValues

    // println "ddp_numPivotOnCol: " + props.getProperty("document.dynamic.userdefined.ddp_numPivotOnCols")
    // println "ddp_numGroupByCol: " + props.getProperty("document.dynamic.userdefined.ddp_numGroupByCols")
    // println "ddp_numKeyHeaders: " + props.getProperty("document.dynamic.userdefined.ddp_numKeyHeaders")
    // println "ddp_numHeaderRows: " + props.getProperty("document.dynamic.userdefined.ddp_numHeaderRows")
    // println "ddp_numHeaderCols: " + props.getProperty("document.dynamic.userdefined.ddp_numHeaderCols")

    /* LOGIC */

    // def pivotedDataConfigsActive = (pivotedDataConfigsArr + newPivotedDataConfigsArr).findAll { it.Active == true }
    def pivotedDataConfigsActive = (pivotedDataConfigsArr).findAll { it.Active == true }
    // println prettyJson(pivotedDataConfigsActive)
    // println pivotedDataConfigsActive.size()

    if (resultTableType == "summary") sqlColumnNamesArr << "Data Table Location"

    // add headers to non-pivot tables. Pivoted tables will already have headers
    def dataArr = isPivot ? [] : [sqlColumnNamesArr]

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
        dataArr << lineArr
    }
    // dataArr.each { println "size: " + it.size() + "  |  " + it }

    int numSubTables = 1
    if (isPivot && !transpose) numSubTables = pivotedDataConfigsActive.SubTableIndex.max() ?: 1
    // println isPivot.toString() + " " + transpose.toString()
    // def numSubTables = (isPivot && !transpose) ? pivotedDataConfigsActive.SubTableIndex.max() : 1
    // println numSubTables
 
    /* --- RowHeaderKeys --- */

    def rowHeaderKeysArr = []
    // for trasnposed - construct a list of keys from the data for
    //   - just key rows
    //   - just groupBy cols
    // println "numHeaderCols: " + numHeaderCols
    // println "numHeaderRows: " + numHeaderRows
    // println "numKeyHeaders: " + numKeyHeaders
    if (isPivot && transpose) {
        // println dataArr.collect{it[0..numKeyHeaders-1].join("====")}
        def topLeftCornerKeys = dataArr.collect{it[0..numKeyHeaders-1].join("___")}[0..numHeaderRows-1]
        // println topLeftCornerKeys
        rowHeaderKeysArr = (topLeftCornerKeys + pivotedDataConfigsActive.ColumnKey).collect{it.split("___")}
    }
    // For non-transposed - construct a list of keys from the data for
    //   - all rows
    //   - just groupBy cols
    // for a non-pivot the number of groupBy cols will be the number of cols in the
    //   sqlColumnNames arr, which is usually all of them
    else rowHeaderKeysArr = dataArr.collect{it[0..numHeaderCols-1]}
    // println rowHeaderKeysArr

    // copy IsKeyColumn from pivotOnColsArr (from the source) to rowHeaderConfigArr
    if (isPivot && transpose) {
        rowHeaderConfigArr.each { configItem ->
            configItem.put("IsKeyColumn", pivotOnColsArr.find { it.Column == configItem.RowHeaderName }?.IsKeyColumn)
        }
    }
 
    // --- Row Header Columns Config --- //
 
    // for pivots - get from rowHeaderConfigArr OR construct if doesn't exist
    //   - the Column Keys doen't exist in the rowHeaderConfigArr so it is constructed from
    //     the data
    //   - for non-transposed - the row headers columns are the gropuBy columns
    //   - for transposed - the row header columns are the pivotOn columns
    // println numKeyHeaders
    def rowHeaderColumnConfigsArr = []
    if (isPivot) {
        rowHeaderConfigArr.findAll{ it.IsKeyColumn != false }.eachWithIndex { configItem, c ->
            def columnKey = !transpose ?
                dataArr.transpose()[c][0..numKeyHeaders-1] :
                dataArr.transpose()[c][0..numHeaderRows-1]

            rowHeaderColumnConfigsArr << [
                ColumnKey: columnKey.join("___"),
                Active: true,
                SubTableIndex: 1,
                ColumnWidth: configItem.RowHeaderWidth,
                RowHeaderKeys: rowHeaderKeysArr.collect{it.join("___")}
            ]
        }
    }
    // println prettyJson(rowHeaderColumnConfigsArr)
 
    /* --- Columns Config Arr --- */
 
    // for non-transposed pivot
    //   - prepend pivotedDataConfigsActive with rowHeaderColumnConfigsArr items
    //   - MAYBE SHOULD CREATE NEW ARR AND APPEND
    // for transposed pivot
    //   - create config records for data columns and append to rowHeaderColumnConfigsArr
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
        // println  pivotedDataConfigsActive.size()
        pivotedDataConfigsActive.eachWithIndex { el, j ->
            // println j
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
            // println [(j>9?j:"0$j"), el.ColumnKey, dataArr.transpose()[j][0..numKeyHeaders-1].collect{j >= numKeyHeaders? it.toUpperCase() : it}.join("___")].join("  |  ")
            // logger.warning([ j>9?j:"0"+j, el.ColumnKey, dataArr.transpose()[j][0..numKeyHeaders-1].collect{j >= numKeyHeaders? it.toUpperCase() : it}.join("___") ].join("  |  "))
            // println dataArr.transpose()[j].size()
            // println dataArr.transpose()[j]

            // if (dataArr.transpose()[j]) {
                newDataArrTransposed << dataArr.transpose()[j]
            // }
            subTableIndexPrev = el.SubTableIndex
            columnsConfigArr << el
            // println ""
            // println j + "  |  " + newDataArrTransposed
        }
        // println ""
        // println newDataArrTransposed.size()
        // println ""
        // newDataArrTransposed.each{ println it.size() + "  |  " + it }
        // println ""
        newDataArr = newDataArrTransposed.transpose()
    }
    else if (isPivot && transpose) {
        // println "YYYYYY"
        // for transposed pivot - construct a list of key for the data columns
        columnsConfigArr = rowHeaderColumnConfigsArr
        def dataColumnKeys = dataArr[0..numHeaderRows-1].transpose()[numKeyHeaders..-1].collect{it.join("___")}
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

        def debugIds = false
        if (debugIds) {
            itemIdsArr = [] as ArrayList
            itemIdsSet = [] as Set
        }

        /* --- tableGroup --- */

        def tableGroupId = reportContentItemId + idDelim + tableDefinitionId
        if (resultTableType != "summary") tableGroupId += idDelim + sqlParamValues

        'tablegroup'('Reference':"id"+md5(tableGroupId), 'id':"id"+md5(tableGroupId)) {  // <tablegroup> has a lowercase g

            props.setProperty("document.dynamic.userdefined.ddp_tableGroupRef", "id"+md5(tableGroupId))

            'h3'('tableHeader':"yes", tableTitleText)
            (1..numSubTables).each { subTableIndex ->

                /* --- table --- */

                def tableId = tableGroupId + subTableIndex
                def pagebreak = ( subTableIndex == 1 ? true : false )

                'table'('class':"output-table", 'border':1, 'width':"100%", 'pagebreak':pagebreak, 'id':"id"+md5(tableId), 'parent_tablegroup':"id"+md5(tableGroupId), seq:subTableIndex) {
                    newDataArr.eachWithIndex{ rowArr, r ->

                        /* --- tr --- */
                        if (!displayHeaders && r < numHeaderRows) true // skip header row if displayHeaders == false
                        else {
                            'tr'() {
                                rowArr.eachWithIndex { item, c ->
                                    // println columnsConfigArr[c]
                                    if (isPivot && !displayHeadersOnSide && c < numHeaderCols) true // skip header col if displayHeadersOnSide == false
                                    else if (subTableIndex == columnsConfigArr[c].SubTableIndex) {

                                        def columnWidth =  columnsConfigArr[c].ColumnWidth
                                        def itemId = tableId + idDelim + columnsConfigArr[0].RowHeaderKeys[r] + idDelim + columnsConfigArr[c].ColumnKey
                                        if (!transpose && (r < numKeyHeaders || c < numHeaderCols)) itemId += idDelim + item
                                        else if (transpose && (r < numHeaderRows || c < numKeyHeaders)) itemId += idDelim + item

                                        if (debugIds) {
                                            println ([subTableIndex,r,(c>9?c:"0"+c),itemId].join("   |   "))
                                            if (itemId in itemIdsArr) {
                                                println ([subTableIndex,r,(c>9?c:"0"+c),itemId].join("   |   ")).toString()
                                            }
                                            itemIdsArr.add(itemId)
                                            itemIdsSet.add(itemId)
                                        }

                                        // tableGroup link for Summary tables
                                        def summaryTableGroupLinkId
                                        if (resultTableType == "summary" && columnsConfigArr[c].ColumnKey == "Data Table Location") {
                                            ArrayList dataTableLocationItemArr = item.split("###", 2)
                                            item = dataTableLocationItemArr[0]
                                            def tableGroupSqlParams = dataTableLocationItemArr[1]
                                            summaryTableGroupLinkId = tableGroupId + idDelim + tableGroupSqlParams
                                            // println item + " :: " + columnsConfigArr[c].ColumnKey + " :: " + summaryTableGroupLinkId
                                        }

                                        /* --- th --- */

                                        // if (r < numHeaderRows) {
                                        if (r < numHeaderRows) {
                                            'th'('columnWidth':columnWidth, id:"id"+md5(itemId), item)
                                            // 'th'('columnWidth':columnWidth, item)
                                        }

                                        /* --- td --- */

                                        else {
                                            if (resultTableType == "summary" && columnsConfigArr[c].ColumnKey == "Data Table Location") {
                                                'td'('columnWidth':columnWidth, id:"id"+md5(itemId), link:"id"+md5(summaryTableGroupLinkId), item)
                                                // 'td'('columnWidth':columnWidth, link:"id"+md5(summaryTableGroupLinkId), item)
                                            }
                                            else {
                                                'td'('columnWidth':columnWidth, id:"id"+md5(itemId), item)
                                                // 'td'('columnWidth':columnWidth, item)
                                            }
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
                // 'br'()
            }
        }

        if (debugIds) {
            println itemIdsArr.size()
            println itemIdsSet.size()
        }

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
