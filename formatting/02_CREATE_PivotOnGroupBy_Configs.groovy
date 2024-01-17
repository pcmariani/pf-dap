import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;
logger = ExecutionUtil.getBaseLogger()

def NEWLINE = System.lineSeparator()
def IFS = /\|\^\|/  // Input Field Separator
def OFS = "|^|"     // Output Field Separator

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    /* INPUTS */

    Boolean transpose = (props.getProperty("document.dynamic.userdefined.ddp_transpose") ?: "false").toBoolean()
    // println transpose

    def columnNames = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnNames") ?: ""
    def columnNamesArr = columnNames ? columnNames.split(IFS).collect{it.toUpperCase()} : []
    // println columnNamesArr

    def pivotedDataConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_PivotedDataConfigs")
    def pivotedDataConfigsArr = pivotedDataConfigsJson ? new JsonSlurper().parseText(pivotedDataConfigsJson).Records : []
    // println prettyJson(pivotedDataConfigsArr)
    // println pivotedDataConfigsArr.size()

    def groupByConfigsJson = props.getProperty("document.dynamic.userdefined.ddp_GroupByConfigs")
    def groupByConfigsArr = groupByConfigsJson ? new JsonSlurper().parseText(groupByConfigsJson).Records : []
    // println prettyJson(groupByConfigsArr)
    // println groupByConfigsArr.size()

    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = new JsonSlurper().parseText(sourcesJson).Records[0]
    // println prettyJson(sources)

    def pivotOnColsArr = sources.PivotOnColumns
        .each{ it.put("Index", columnNamesArr.indexOf(it.Column.toUpperCase())) }
    // println "pivotOnColsArr: " + prettyJson(pivotOnColsArr)
    def groupByColsArr = sources.PivotGroupByColumns
        .each{ it.put("Index", columnNamesArr.indexOf(it.Column.toUpperCase())) }
    // println "groupByColsArr: " + prettyJson(groupByColsArr)

    /* LOGIC */

    def reader = new BufferedReader(new InputStreamReader(is))
    def outData = new StringBuffer()
    def pivotOnKeySetMap = [:]
    def groupByKeySetMap = [:]

    while ((line = reader.readLine()) != null ) {
        outData.append(line + NEWLINE)
        // replace a blank last element, then split, then replace the replacement with an empty string
        def lineArr = line
            .replaceFirst(/$IFS\s*$/, "${IFS}LAST_ELEMENT_IS_BLANK")
            .split(/\s*$IFS\s*/)
            .collect{ it == "LAST_ELEMENT_IS_BLANK" ? "" : it }

        def pivotOnKey = pivotOnColsArr.collect{ if (it.IsKeyColumn) lineArr[it.Index]; else ""}.join(OFS)
        def pivotOnLabels = pivotOnColsArr.collect{ lineArr[it.Index]}.join(OFS)

        def groupByKey = groupByColsArr.collect{ if (it.IsKeyColumn) lineArr[it.Index]; else ""}.join(OFS)
        def groupByLabels = groupByColsArr.collect{ lineArr[it.Index]}.join(OFS)

        // if (line =~ /Intact Ig/) {
        //     println pivotOnKey
        //     println groupByKey
        //     println lineArr
        // }

        pivotOnKeySetMap[upper(pivotOnKey)] = pivotOnLabels
        groupByKeySetMap[upper(groupByKey)] = groupByLabels
    }
    // println prettyJson(pivotOnKeySetMap)
    // println prettyJson(groupByKeySetMap)



    /* --- conditionally create new pivotedDataConfig records --- */

    // collect all pivot keys which are in the data but not in pivotedDataConfig records;
    // for each one, create a new pivotedDataConfig record
    def pivotOnKeysInDataButNotInConfig = pivotOnKeySetMap.findAll {
        !pivotedDataConfigsArr.ColumnKey.contains(it.key)
    }
    // println prettyJson(pivotOnKeysInDataButNotInConfig)
    // println pivotOnKeysInDataButNotInConfig.size()

    def columnKeyCounter = pivotedDataConfigsArr.ColumnKey.size() + 1
    def subTableMaxIndex = pivotedDataConfigsArr.SubTableIndex.max() ?: 1
    def newPivotedDataConfigsArr = []

    pivotOnKeysInDataButNotInConfig.each { key, label ->
        newPivotedDataConfigsArr << [
            ColumnKey: key,
            ColumnLabels: label,
            Active: true,
            ColumnIndex: columnKeyCounter++,
            SubTableIndex: subTableMaxIndex,
            ColumnWidth: 10,
            SuppressIfNoDataForAllRows: true,
            // PivotedItemLabels: label.split(OFS).collect{ [Name: it, Label: it] }
        ]
    }
    // println "newPivotedDataConfigsArr: " + prettyJson(newPivotedDataConfigsArr)
    // println newPivotedDataConfigsArr.size()




    /* --- conditionally create new groupByConfig records --- */

    // collect all pivot keys which are in the data but not in groupByConfig records;
    // for each one, create a new groupByConfig record
    def groupByKeysInDataButNotInConfig = groupByKeySetMap.findAll {
        !groupByConfigsArr.RowKey.contains(it.key)
    }
    // println prettyJson(groupByKeysInDataButNotInConfig)
    // println groupByKeysInDataButNotInConfig.size()

    def rowKeyCounter = groupByConfigsArr.RowKey.size() + 1
    def newGroupByConfigsArr = []

    groupByKeysInDataButNotInConfig.each { key, label ->
        newGroupByConfigsArr << [
            RowKey: key,
            RowLabels: label,
            Active: true,
            RowIndex: rowKeyCounter++,
            SuppressIfNoDataForAllCols: true,
            // GroupByLabels: label.split(OFS).collect{ [Name: it, Label: it] }
        ]
    }
    // println "newGroupByConfigsArr: " + prettyJson(newGroupByConfigsArr)
    // println newGroupByConfigsArr.size()




    /* OUTPUT */

    def newPivotedDataConfigsWrapped = [:]
    def newGroupByConfigsWrapped = [:]
    if (newPivotedDataConfigsArr || newGroupByConfigsArr) {
        def requestor = props.getProperty("document.dynamic.userdefined.ddp_Requestor")
        def reportContentItem_DynamicTableId = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem_DynamicTableId")
        def tableDefinitionId = props.getProperty("document.dynamic.userdefined.ddp_TableDefinitionId")
        def sourceSqlQueryId = props.getProperty("document.dynamic.userdefined.ddp_SourceSqlQueryId")

        if (newPivotedDataConfigsArr) {
            newPivotedDataConfigsWrapped = [
                Requestor: requestor,
                ReportContentItem_DynamicTableId: reportContentItem_DynamicTableId,
                TableDefinitionId: tableDefinitionId,
                SourceId: sourceSqlQueryId,
                Records: newPivotedDataConfigsArr
            ]
        }

        if (newGroupByConfigsArr) {
            newGroupByConfigsWrapped = [
                Requestor: requestor,
                ReportContentItem_DynamicTableId: reportContentItem_DynamicTableId,
                TableDefinitionId: tableDefinitionId,
                SourceId: sourceSqlQueryId,
                Records: newGroupByConfigsArr
            ]
        }
    }
    // println "newPivotedDataConfigsWrapped: " + prettyJson(newPivotedDataConfigsWrapped)
    // println "newGroupByConfigsWrapped: " + prettyJson(newGroupByConfigsWrapped)

    int maxColumnIndex = pivotedDataConfigsArr ? pivotedDataConfigsArr.ColumnIndex.max() : 0
    // props.setProperty("document.dynamic.userdefined.ddp_pivotedDataConfigs_maxIndex", maxColumnIndex as String)
    // props.setProperty("document.dynamic.userdefined.ddp_numPivotOnCols", pivotOnColsArr.size() as String)
    // props.setProperty("document.dynamic.userdefined.ddp_numGroupByCols", groupByColsArr.size() as String)
    // props.setProperty("document.dynamic.userdefined.ddp_numKeyHeaders", pivotOnColsArr.findAll{it.IsKeyColumn == true}.size() as String)
    // props.setProperty("document.dynamic.userdefined.ddp_numHeaderRows", (!transpose ? pivotOnColsArr.size() : groupByColsArr.size()) as String)
    // props.setProperty("document.dynamic.userdefined.ddp_numHeaderCols", (!transpose ? groupByColsArr.size() : pivotOnColsArr.size()) as String)
    props.setProperty("document.dynamic.userdefined.ddp_NewPivotedDataConfigs", JsonOutput.toJson(newPivotedDataConfigsWrapped))
    props.setProperty("document.dynamic.userdefined.ddp_NewGroupByConfigs", JsonOutput.toJson(newGroupByConfigsWrapped))
    // props.setProperty("document.dynamic.userdefined.ddp_GroupByConfigsConsolidated", JsonOutput.toJson(newGroupByConfigsWrapped))
    // props.setProperty("document.dynamic.userdefined.ddp_PivotedDataConfigsConsolidated", JsonOutput.toJson(newPivotedDataConfigsWrapped))

    // println "pivotedDataConfigs_maxIndex: " + props.getProperty("document.dynamic.userdefined.ddp_pivotedDataConfigs_maxIndex")
    // println "numPivotOnCols: " + props.getProperty("document.dynamic.userdefined.ddp_numPivotOnCols")
    // println "numGroupByCols: " + props.getProperty("document.dynamic.userdefined.ddp_numGroupByCols")
    // println "numKeyHeaders: "  + props.getProperty("document.dynamic.userdefined.ddp_numKeyHeaders")
    // println "numHeaderRows: "  + props.getProperty("document.dynamic.userdefined.ddp_numHeaderRows")
    // println "numHeaderCols: "  + props.getProperty("document.dynamic.userdefined.ddp_numHeaderCols")

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

 // upper function also strips out special characters - this could be a problem
private static String upper(String str) { return str.replaceAll("\\P{Print}","").toUpperCase() }
private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
