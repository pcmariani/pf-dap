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
    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = new JsonSlurper().parseText(sourcesJson).Records[0]
    // println prettyJson(sources)

    /* LOGIC */

    def pivotOnColsArr = sources.PivotOnColumns
        .each{ it.put("Index", columnNamesArr.indexOf(it.Column.toUpperCase())) }
    // println prettyJson(pivotOnColsArr)
    def groupByColsArr = sources.PivotGroupByColumns
        .each{ it.put("Index", columnNamesArr.indexOf(it.Column.toUpperCase())) }
    // println groupByColsArr

    def reader = new BufferedReader(new InputStreamReader(is))
    def outData = new StringBuffer()
    def pivotOnKeySetMap = [:]

    while ((line = reader.readLine()) != null ) {
        outData.append(line + NEWLINE)
        // Split the line.
        // Unfortunately the length of the array is calcualted to the last element populated in the line.
        // So lines can have different lengths, even though the number of delimiters is the same.
        // We replace a blank last element, then split, then replace the replacement with an empty string
        def lineArr = line
            .replaceFirst(/$IFS\s*$/, "${IFS}LAST_ELEMENT_IS_BLANK")
            .split(/\s*$IFS\s*/)
            .collect{ it == "LAST_ELEMENT_IS_BLANK" ? "" : it }

        def pivotOnMapKey_AllCols = pivotOnColsArr.collect { lineArr[it.Index] ?: "-" }.join("___")
        def pivotOnMapKey_KeyCols = pivotOnColsArr.collect { if (it.IsKeyColumn) lineArr[it.Index]; else "REMOVE" }.join("___").replaceAll("___REMOVE","")

        pivotOnKeySetMap[upper(pivotOnMapKey_KeyCols)] = [
            AllCols: pivotOnMapKey_AllCols,
            KeyCols: pivotOnMapKey_KeyCols
        ]
    }
    // println prettyJson(pivotOnKeySetMap)

    /* --- conditionally create new pivotedDataConfig records --- */

    // collect all pivot keys which are in the data but not in pivotedDataConfig records;
    // for each one, create a new pivotedDataConfig record
    def pivotOnKeysInDataButNotInConfig = pivotOnKeySetMap.findAll { key, value ->
        !pivotedDataConfigsArr.ColumnKey.contains(key)
    }
    // println prettyJson(pivotOnKeysInDataButNotInConfig)
    // println pivotOnKeysInDataButNotInConfig.size()

    def columnKeyCounter = pivotedDataConfigsArr.ColumnKey.size() + 1
    def subTableMaxIndex = pivotedDataConfigsArr.SubTableIndex.max() ?: 1
    def newPivotedDataConfigsArr = []

    pivotOnKeysInDataButNotInConfig.each { key, value ->
        newPivotedDataConfigsArr << [
            ColumnKey: key,
            Active: true,
            ColumnIndex: columnKeyCounter++,
            SubTableIndex: subTableMaxIndex,
            ColumnWidth: 10,
            SuppressIfNoDataForAllRows: true,
            PivotedItemLabels: value.KeyCols.split("___").collect{ columnName ->
                [Name: columnName, Label: columnName]
            }
        ]
    }
    // println prettyJson(newPivotedDataConfigsArr)
    // println newPivotedDataConfigsArr.size()

    /* OUTPUT */

    def newPivotedDataConfigsWrapped = [:]
    if (newPivotedDataConfigsArr) {
        def requestor = props.getProperty("document.dynamic.userdefined.ddp_Requestor")
        def reportContentItem_DynamicTableId = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem_DynamicTableId")
        def tableDefinitionId = props.getProperty("document.dynamic.userdefined.ddp_TableDefinitionId")
        def sourceSqlQueryId = props.getProperty("document.dynamic.userdefined.ddp_SourceSqlQueryId")

        newPivotedDataConfigsWrapped = [
            Requestor: requestor,
            ReportContentItem_DynamicTableId: reportContentItem_DynamicTableId,
            TableDefinitionId: tableDefinitionId,
            SourceId: sourceSqlQueryId,
            Records: newPivotedDataConfigsArr
        ]
    }
    // println prettyJson(newPivotedDataConfigsWrapped)

    int maxColumnIndex = pivotedDataConfigsArr ? pivotedDataConfigsArr.ColumnIndex.max() : 0
    props.setProperty("document.dynamic.userdefined.ddp_pivotedDataConfigs_maxIndex", maxColumnIndex as String)
    props.setProperty("document.dynamic.userdefined.ddp_numPivotOnCols", pivotOnColsArr.size() as String)
    props.setProperty("document.dynamic.userdefined.ddp_numGroupByCols", groupByColsArr.size() as String)
    props.setProperty("document.dynamic.userdefined.ddp_numKeyHeaders", pivotOnColsArr.findAll{it.IsKeyColumn == true}.size() as String)
    props.setProperty("document.dynamic.userdefined.ddp_numHeaderRows", (!transpose ? pivotOnColsArr.size() : groupByColsArr.size()) as String)
    props.setProperty("document.dynamic.userdefined.ddp_numHeaderCols", (!transpose ? groupByColsArr.size() : pivotOnColsArr.size()) as String)
    props.setProperty("document.dynamic.userdefined.ddp_NewPivotedDataConfigs", JsonOutput.toJson(newPivotedDataConfigsWrapped))

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
