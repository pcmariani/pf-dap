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
    println "#DEBUG transpose: " + transpose
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

    // should probably be moved - the logic for summary tables is disorganized
    if (resultTableType == "summary") sqlColumnNamesArr << "Data Table Location"

    /* --- read data ---*/

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
    // transpose = false
    if (transpose) dataArr = dataArr.transpose()
    // dataArr.each { println "size: " + it.size() + "  |  " + it }
    // dataArr.each { println it }
 

    // --- Columns Config --- //

    def columnsConfigArr = []

    if (isPivot) {

        if (!transpose) {

            def rowHeaderKeysArr = dataArr.collect{it[0..numHeaderCols-1]}
            // println "#DEBUG rowHeaderKeysArr: " + rowHeaderKeysArr

            def rowHeaderColumnConfigsArr = []
            rowHeaderConfigArr.findAll{ it.IsKeyColumn != false }.eachWithIndex { configItem, c ->
                rowHeaderColumnConfigsArr << [
                    ColumnKey: dataArr.transpose()[c][0..numKeyHeaders-1].join("___"),
                    ColumnWidth: configItem.RowHeaderWidth,
                    RowHeaderKeys: rowHeaderKeysArr.collect{it.join("___")}
                ]
            }
            // println "#DEBUG rowHeaderColumnConfigsArr: " + prettyJson(rowHeaderColumnConfigsArr)

            def activePivotedDataConfigsArr = pivotedDataConfigsArr.findAll { it.Active == true }.collect{it.subMap("ColumnKey","ColumnWidth")}
            // println "#DEBUG activePivotedDataConfigsArr: " + activePivotedDataConfigsArr

            columnsConfigArr = rowHeaderColumnConfigsArr.reverse() + activePivotedDataConfigsArr
            // println "#DEBUG columnsConfigArr" + prettyJson(columnsConfigArr)
        }

        else if (transpose) {

            def topLeftCornerKeys = dataArr.collect{it[0..numKeyHeaders-1].join("___")}[0..numHeaderRows-1]
            // println "#DEBUG topLeftCornerKeys: " + topLeftCornerKeys

            def rowHeaderKeysArr = (topLeftCornerKeys + columnsConfigArr.ColumnKey).collect{it.split("___")}
            // println "#DEBUG rowHeaderKeysArr: " + rowHeaderKeysArr

            def rowHeaderColumnConfigsArr = []
            rowHeaderConfigArr.eachWithIndex { configItem, c ->
                def isKeyColumn = pivotOnColsArr.find { it.Column == configItem.RowHeaderName }.IsKeyColumn
                if (isKeyColumn) {
                    rowHeaderColumnConfigsArr << [
                        ColumnKey: dataArr.transpose()[c][0..numHeaderRows-1].join("___"),
                        ColumnWidth: configItem.RowHeaderWidth,
                        RowHeaderKeys: rowHeaderKeysArr.collect{it.join("___")}
                    ]
                }
            }
            // println "#DEBUG rowHeaderKeysArr: " + rowHeaderKeysArr
            // println "#DEBUG rowHeaderConfigArr: " + rowHeaderConfigArr

            columnsConfigArr = rowHeaderColumnConfigsArr

            def dataColumnKeys = dataArr[0..numHeaderRows-1].transpose()[numKeyHeaders..-1].collect{it.join("___")}
            // println "#DEBUG dataColumnKeys: " + dataColumnKeys

            dataColumnKeys.each { columnKey ->
                columnsConfigArr << [
                    ColumnKey: columnKey,
                    ColumnWidth: 10,
                ]
            }
            // println "#DEBUG columnsConfigArr" + prettyJson(columnsConfigArr)
        }

    }

    else if (!isPivot) {
        def rowHeaderKeysArr = dataArr.collect{it[0..numHeaderCols-1]}
        dataArr[0].each { header ->
            columnsConfigArr << [
                ColumnKey: header,
                ColumnWidth: 10,
                RowHeaderKeys: rowHeaderKeysArr.collect{it.join("___")}
            ]
        }
    }

    // println "#DEBUG columnsConfigArr" + prettyJson(columnsConfigArr)
    // println "#DEBUG columnsConfigArr SIZE:" + columnsConfigArr.size()
 
    /* --- Build the HTML --- */

    def idDelim = "-----"

    def xmlMarkup = new StreamingMarkupBuilder().bind {

        def debugIds = false
        // def debugIds = true
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
                                    def itemId = tableId + idDelim + columnsConfigArr[0].RowHeaderKeys[r] + idDelim + columnsConfigArr[c].ColumnKey
                                    if (!transpose && (r < numKeyHeaders || c < numHeaderCols)) itemId += idDelim + item
                                    else if (transpose && (r < numHeaderRows || c < numKeyHeaders)) itemId += idDelim + item

                                        if (debugIds) {
                                            println ([r,(c>9?c:"0"+c),itemId].join("   |   "))
                                            if (itemId in itemIdsArr) {
                                                println ([r,(c>9?c:"0"+c),itemId].join("   |   ")).toString()
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
            println itemIdsArr.size()
            println itemIdsSet.size()
        }

    }

    String outData
    outData = groovy.xml.XmlUtil.serialize(xmlMarkup).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim() //.replaceAll(/<tr\s*?\/\s*>/,"")
    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
 
    // is = new ByteArrayInputStream(xmlMarkup.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}
 
private static String prettyJson(def thing) {
    return JsonOutput.prettyPrint(JsonOutput.toJson(thing))
}
 
private static String md5(String str) {
    return str//.md5()
}
