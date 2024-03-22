import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import groovy.xml.StreamingMarkupBuilder;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()
def IFS = /\|\^\|/  // Input Field Separater
def OFS = "|^|"     // Output Field Separator
def DBIFS = "^^^"    // Database Field Separator

// def pivotedDataConfigsJson = ExecutionUtil.getDynamicProcessProperty("DPP_PivotedDataConfigs")
 
for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);
 
    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated")
    def activePivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson)?.Records : []
    // println prettyJson(activePivotedDataConfigsArr)
    // println activePivotedDataConfigsArr.size()

    def groupByConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_GroupByConfigsConsolidated")
    def activeGroupByConfigsArr = groupByConfigsJson ? new JsonSlurper().parseText(groupByConfigsJson)?.Records : []
    // println prettyJson(activeGroupByConfigsArr)
    // println activeGroupByConfigsArr.size()

    def topLeftCornerKeysArrJson = props.getProperty("document.dynamic.userdefined.ddp_topLeftCornerKeysArrJson")
    def topLeftCornerKeysArr = topLeftCornerKeysArrJson ? new JsonSlurper().parseText(topLeftCornerKeysArrJson) : []
    // println prettyJson(topLeftCornerKeysArr)

    def rowHeaderConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_RowHeaderConfig")
    def rowHeaderConfigArr = rowHeaderConfigsJson ? new JsonSlurper().parseText(rowHeaderConfigsJson)?.Records[0].RowHeaderConfig : []
    // println prettyJson(rowHeaderConfigArr)
    // println rowHeaderConfigArr.size()

    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = sourcesJson ? new JsonSlurper().parseText(sourcesJson).Records[0] : []
    // println prettyJson(sources)

    Boolean isPivot = (props.getProperty("document.dynamic.userdefined.ddp_isPivot") ?: "true").toBoolean()
    // println isPivot
    Boolean displayHeaders = (props.getProperty("document.dynamic.userdefined.ddp_displayHeaders") ?: "true").toBoolean()
    // println "displayHeaders: " + displayHeaders
    Boolean displayHeadersOnSide = (props.getProperty("document.dynamic.userdefined.ddp_displayHeadersOnSide") ?: "true").toBoolean()
    // println "displayHeadersOnSide: " + displayHeadersOnSide

    int tableDefinitionId = (props.getProperty("document.dynamic.userdefined.ddp_TableDefinitionId") ?: "1") as int
    // println tableDefinitionId
    int reportContentItemId = (props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem_DynamicTableId") ?: "1") as int
    // println reportContentItemId

    def resultTableType = props.getProperty("document.dynamic.userdefined.ddp_resultTableType") ?: "data"
    if (resultTableType =~ /(?i)Summary/) resultTableType = "summary"
    // println resultTableType

    def tableTitleText = props.getProperty("document.dynamic.userdefined.ddp_tableTitleText")
    // println tableTitleText

    def sqlParamValues = props.getProperty("document.dynamic.userdefined.ddp_sqlParamValues")
    // println sqlParamValues
    def sqlColumnNames = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnNames")
    ArrayList sqlColumnNamesArr = sqlColumnNames ? sqlColumnNames.split(/\s*$IFS\s*/) : []
    // println sqlColumnNamesArr

    def groupByColsArr = sourcesJson ? sources.PivotGroupByColumns : []
    // println groupByColsArr
    def pivotOnColsArr = sourcesJson ? sources.PivotOnColumns : []
    // println pivotOnColsArr
    int numHeaderRows = isPivot ? pivotOnColsArr.size() : 1
    // println numHeaderRows
    int numHeaderCols = isPivot ? groupByColsArr.size() : sqlColumnNamesArr.size()
    // println numHeaderCols
 
    // should probably be moved - the logic for summary tables is disorganized
    if (resultTableType == "summary") sqlColumnNamesArr << "Data Table Location"



    /* LOGIC */


    /* --- read data ---*/

    // add headers to non-pivot tables. Pivoted tables will already have headers
    def dataArr = isPivot ? [] : [sqlColumnNamesArr]

    def reader = new BufferedReader(new InputStreamReader(is))
    while ((line = reader.readLine()) != null ) {
        // replace a blank last element, then split, then replace the replacement with an empty string
        def lineArr = line
            .replaceFirst(/$IFS\s*$/, "${IFS}LAST_ELEMENT_IS_BLANK")
            .split(/\s*$IFS\s*/)
            .collect{ it == "LAST_ELEMENT_IS_BLANK" ? "" : it }
        dataArr << lineArr
    }
    // dataArr.each { println "size: " + it.size() + "  |  " + it }
    // println dataArr.size()

    // --- Columns Config --- //

    def columnsConfigArr = []

    if (isPivot) {
        def rowHeaderKeysArr = topLeftCornerKeysArr.collect{ it.join(DBIFS) } + activeGroupByConfigsArr.RowKey
        // println "#DEBUG rowHeaderKeysArr: " + rowHeaderKeysArr

        def rowHeaderColumnConfigsArr = []
        rowHeaderConfigArr.eachWithIndex { configItem, c ->
            rowHeaderColumnConfigsArr << [
                ColumnKey: groupByColsArr[c].Label ?: groupByColsArr[c].Column,
                ColumnWidth: configItem.RowHeaderWidth,
                RowHeaderKeys: rowHeaderKeysArr
            ]
            if (c > 0) rowHeaderColumnConfigsArr[c].remove("RowHeaderKeys")
        }
        // println "#DEBUG rowHeaderColumnConfigsArr: " + prettyJson(rowHeaderColumnConfigsArr)

        columnsConfigArr = rowHeaderColumnConfigsArr + activePivotedDataConfigsArr
        // println "#DEBUG columnsConfigArr" + prettyJson(columnsConfigArr)
    }

    else if (!isPivot) {
        def rowHeaderKeysArr = dataArr.collect{it[0..numHeaderCols-1]}
        // println "#DEBUG rowHeaderKeysArr: " + rowHeaderKeysArr

        dataArr[0].each { header ->
            columnsConfigArr << [
                ColumnKey: header,
                ColumnWidth: 10,
                RowHeaderKeys: rowHeaderKeysArr.collect{it.join(OFS)}
            ]
        }
    }

    // println "#DEBUG columnsConfigArr" + prettyJson(columnsConfigArr)
    // println "#DEBUG columnsConfigArr SIZE:" + columnsConfigArr.size()
 
    /* --- Build the HTML --- */

    def idDelim = "-----"

    def xmlMarkup = new StreamingMarkupBuilder().bind {

        def debugIds
        // debugIds = true
        if (debugIds) {
            itemIdsArr = [] as ArrayList
            itemIdsSet = [] as Set
        }

        /* --- tableGroup --- */

        def tableGroupId = reportContentItemId + idDelim + tableDefinitionId

        if (resultTableType != "summary") {
            tableGroupId += idDelim + sqlParamValues
        }

        'tablegroup'('Reference':"id"+md5(tableGroupId), 'id':"id"+md5(tableGroupId)) {  // <tablegroup> has a lowercase g

            props.setProperty("document.dynamic.userdefined.ddp_tableGroupRef", "id"+md5(tableGroupId))

            'h3'('tableHeader':"yes", tableTitleText)

            /* --- table --- */

            def tableId = tableGroupId

            'table'('class':"output-table", 'border':1, 'width':"100%", 'id':"id"+md5(tableId), 'parent_tablegroup':"id"+md5(tableGroupId)) {
                // 'table'('class':"output-table", 'border':1, 'width':"100%") {
                dataArr.eachWithIndex{ rowArr, r ->

                    /* --- tr --- */

                    if (!displayHeaders && r < numHeaderRows) true // skip header row if displayHeaders == false

                    else {
                        'tr'() {
                            rowArr.eachWithIndex { item, c ->
                                // println columnsConfigArr[c]

                                if (isPivot && !displayHeadersOnSide && c < numHeaderCols) true // skip header col if displayHeadersOnSide == false

                                else {

                                    def columnWidth =  columnsConfigArr[c].ColumnWidth
                                    // println r + " " + columnsConfigArr[0]
                                    def itemId = tableId + idDelim + columnsConfigArr[0].RowHeaderKeys[r] + idDelim + columnsConfigArr[c].ColumnKey

                                    if (isPivot && r < numHeaderRows) {
                                        def allValuesSameForThisHeaderRow = rowArr[numHeaderCols..-1].unique().size() == 1
                                        if (pivotOnColsArr[r].IsKeyColumn || allValuesSameForThisHeaderRow) itemId += idDelim + item
                                    }

                                    if (debugIds) {
                                        println ([(r>9?r:"0"+r),(c>9?c:"0"+c),itemId].join("   |   ")).toString()
                                        // if (itemId in itemIdsArr) {
                                            // println (["D"+(r>9?r:"0"+r),(c>9?c:"0"+c),itemId].join("   |   ")).toString()
                                        // }
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
                                        // 'th'(item)
                                    }

                                    /* --- td --- */

                                    else {
                                        if (resultTableType == "summary" && columnsConfigArr[c].ColumnKey == "Data Table Location") {
                                            'td'('columnWidth':columnWidth, id:"id"+md5(itemId), link:"id"+md5(summaryTableGroupLinkId), item)
                                            // 'td'('columnWidth':columnWidth, link:"id"+md5(summaryTableGroupLinkId), item)
                                            // 'td'(link:"id"+md5(summaryTableGroupLinkId), item)
                                        }
                                        else {
                                            'td'('columnWidth':columnWidth, id:"id"+md5(itemId), item)
                                            // 'td'('columnWidth':columnWidth, item)
                                            // 'td'(item)
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }

        if (debugIds) {
            println "#DEBUG num ids:        " + itemIdsArr.size()
            println "#DEBUG num unique ids: " + itemIdsSet.size()
        }

    }

    String outData
    outData = groovy.xml.XmlUtil.serialize(xmlMarkup).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim() //.replaceAll(/<tr\s*?\/\s*>/,"")
    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));

    props.setProperty("document.dynamic.userdefined.ddp_numHeaderRows", numHeaderRows.toString())
    props.setProperty("document.dynamic.userdefined.ddp_numHeaderCols", numHeaderCols.toString())
 
    // is = new ByteArrayInputStream(xmlMarkup.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}
 
private static String prettyJson(def thing) {
    return JsonOutput.prettyPrint(JsonOutput.toJson(thing))
}
 
private static String md5(String str) {
    return str//.md5()
}

