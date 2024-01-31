import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();


for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    /* INPUTS */

    Boolean hasSummarySource = (ExecutionUtil.getDynamicProcessProperty("DPP_HasSummarySource") ?: "false").toBoolean()
    // println hasSummarySource
    def sectionNumber = ExecutionUtil.getDynamicProcessProperty("DPP_SectionNumber") ?: "0.0.0.0.0"
    // println sectionNumber
    int tableInstanceIndex = (props.getProperty("document.dynamic.userdefined.ddp_tableInstanceIndex") ?: "1") as int
    // println tableInstanceIndex
    def tableInstanceRoot = new JsonSlurper().parse(is).Records[0]
    def userInputValues = tableInstanceRoot.UserInputValues
    // println userInputValues
    def tableDefinitionJson = props.getProperty("document.dynamic.userdefined.ddp_TableDefinition")
    def tableDefinition = new JsonSlurper().parseText(tableDefinitionJson).Records[0]
    // println tableDefinition
    def userInputsJson = props.getProperty("document.dynamic.userdefined.ddp_UserInputs")
    def userInputs = new JsonSlurper().parseText(userInputsJson).Records
    // println userInputs
    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = new JsonSlurper().parseText(sourcesJson).Records
    // println prettyJson(sources)
    def reportContentItemJson = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem")
    def reportContentItem
    if (reportContentItemJson) reportContentItem = new JsonSlurper().parseText(reportContentItemJson).Records[0]
    // println reportContentItem
    def virtualColumnsJson = props.getProperty("document.dynamic.userdefined.ddp_VirtualColumns")
    def virtualColumns = virtualColumnsJson ? new JsonSlurper().parseText(virtualColumnsJson).Records : []
    // println prettyJson(virtualColumns)

    /* LOGIC */

    def pivotOnUserInputIdsArr
    def groupByUserInputIdsArr
    def masterValuesMap = [:]

    sources.sort { ["Data Table","Summary Table","Calculations Table"].indexOf(it.ResultTableType) }.each { source ->

        /* --- collect all values (from userInputValues or from a global variable) --- */

        // println "\n---------- $source.ResultTableType -----------\n"

        def valuesArr = []
        def valuesMap = [:]
        def valuesArr2 = []
        source.ParamUserInputMap.findAll{it.UserInputId != null}.each { param ->
            def valuesMap2 = [:]

            def userInput = userInputs.find{it.UserInputId == param.UserInputId}
            def userInputName = userInput.UserInputName

            valuesMap2.userInputName = userInputName
            valuesMap2.userInputId = userInput.UserInputId
            valuesMap2.paramName = param.ParamName

            if (source.ResultTableType =~ /(?i)data/) {
                valuesMap2.sourceConfigLocation = 
                    source.PivotOnColumns.findAll { it.Column == param.ParamName } ? "PivotOn" :
                    source.PivotGroupByColumns.findAll { it.Column == param.ParamName } ? "GroupBy" :
                    "none"
            }
            else if (source.ResultTableType =~ /(?i)summary/) {
                valuesMap2.sourceConfigLocation = masterValuesMap.'Data Table'.find { 
                    it.userInputId == userInput.UserInputId
                }?.sourceConfigLocation
            }

            
            def globalVariableName = userInput.GlobalVariableName?.replaceAll(" ","_")
            def userInputValue = userInputValues.find{it.UserInputId == param.UserInputId}?.UserInputValue
            if (userInputValue) {
                valuesArr << userInputValue
                valuesMap[userInputName] = userInputValue
                valuesMap2.value = userInputValue
                // println userInputValue
            }
            else if (globalVariableName) {
                def globalVarValue = ExecutionUtil.getDynamicProcessProperty("DPP_" + globalVariableName)
                if (!globalVarValue) globalVarValue = ExecutionUtil.getDynamicProcessProperty("dpp_" + globalVariableName)
                if (!globalVarValue) globalVarValue = ExecutionUtil.getDynamicProcessProperty(globalVariableName)
                if (!globalVarValue) globalVarValue = reportContentItem.SampleGlobalVariables.find{it.Name.replaceAll(" ","_") == globalVariableName}?.Value
                valuesArr << globalVarValue
                valuesMap[userInputName] = globalVarValue
                valuesMap2.value = globalVarValue
                // println globalVarValue
            }
            valuesArr2 << valuesMap2
        }

        if (virtualColumns) {
            virtualColumns.each { vc ->
                def valuesMap2 = [:]
                def virtualColumnLabel = vc.ColumnLabel
                def virtualColumnValue = vc.VirtualColumnRows?.find{
                    it.TableInstanceId == tableInstanceRoot.TableInstanceId
                }?.Value
                if (virtualColumnValue) {
                    valuesMap[virtualColumnLabel] = virtualColumnValue
                    valuesMap2.userInputName = virtualColumnLabel
                    valuesMap2.userInputId = "VIRTUAL"
                    valuesMap2.value = virtualColumnValue
                }
                valuesArr2 << valuesMap2
            }
        }


        // println "-----"
        // valuesArr.each { println it }
        // println valuesMap
        masterValuesMap."$source.ResultTableType" = valuesArr2
        masterValuesMap.each { m -> m.each { it.each { println it } }; println ""}


        /* --- construct table title --- */

        // set tableTitleText
        def tableTitleText = "Table Title Text Not Yet Configured"
        if (source.ResultTableType =~ /(?i)Summary/){
            tableTitleText = sectionNumber + "-1. " +
                    ( tableDefinition.TableTitleText_Summary != null && tableDefinition.TableTitleText_Summary != ""
                     ? tableDefinition.TableTitleText_Summary
                     : tableTitleText )
        }
        else if (source.ResultTableType =~ /(?i)Data/) {
            if (tableInstanceRoot.TableTitleOverride) {
                tableTitleText = sectionNumber + "-" + tableInstanceIndex.toString() + ". " +
                    ( tableDefinition.TableTitleOverride != null && tableDefinition.TableTitleOverride != ""
                     ? tableDefinition.TableTitleOverride
                     : tableTitleText )
            }
            else {
                // println tableTitleText
                // println tableDefinition.TableTitleText
                tableTitleText = sectionNumber + "-" + tableInstanceIndex.toString() + ". " +
                    ( tableDefinition.TableTitleText != null && tableDefinition.TableTitleText != ""
                     ? tableDefinition.TableTitleText
                     : tableTitleText )
            }
        }
        // println tableTitleText

        // for tableTitleText, replace placeholders with values, apply stringReplacements
        def stringReplacementsArr = tableDefinition.TableTitleStringReplacements
        // println stringReplacementsArr
        (tableTitleText =~ /\{\{(.*?)\}\}/).collect{match -> match[1]}.unique().each() { name ->
            def value = valuesMap[name]
            // println name
            if (!value) {
                def propName = name.replaceAll(" ","_")
                value = ExecutionUtil.getDynamicProcessProperty("DPP_" + propName)
                if (!value) value = ExecutionUtil.getDynamicProcessProperty(propName)
                else if (!value) value = props.getProperty("document.dynamic.userdefined.ddp_" + propName)
                else if (!value) value = props.getProperty("document.dynamic.userdefined." + propName)
            }
            // println value
            if (value) {
                def replacementsArr = stringReplacementsArr.findAll{ name == it.PlaceHolder.replaceAll(/[\{\{\}\}]/,"") }
                replacementsArr.each { replacement ->
                    value = value.replaceAll(replacement.SearchText, replacement.ReplaceText)
                }
                tableTitleText = tableTitleText.replaceAll(/\{\{$name\}\}/, value)
            }
        }
        tableTitleText = tableTitleText.replaceAll("<", "&lt;").replaceAll(">", "&gt;")
        // println tableTitleText

        /* OUTPUT */

        def outData = [
            Message: "For information only",
            TableInstanceId: tableInstanceRoot.TableInstanceId,
            SourceId: source.SourceSqlQueryId,
            DbSystem: source.DbSystem,
            SqlStatement: source.SqlStatement,
            SqlParamValues: valuesArr.join(";"),
            TableTitleText: tableTitleText
        ]

        props.setProperty("document.dynamic.userdefined.ddp_Sources", JsonOutput.toJson([Records:[source]]))
        props.setProperty("document.dynamic.userdefined.ddp_TableInstanceId", tableInstanceRoot.TableInstanceId.toString())
        props.setProperty("document.dynamic.userdefined.ddp_SourceSqlQueryId", source.SourceSqlQueryId.toString())
        props.setProperty("document.dynamic.userdefined.ddp_resultTableType", source.ResultTableType)
        props.setProperty("document.dynamic.userdefined.ddp_dbSystem", source.DbSystem)
        props.setProperty("document.dynamic.userdefined.ddp_sqlStatement", source.SqlStatement)
        props.setProperty("document.dynamic.userdefined.ddp_sqlParamValues", valuesArr.join(";"))
        props.setProperty("document.dynamic.userdefined.ddp_tableTitleText", tableTitleText)
        props.setProperty("document.dynamic.userdefined.ddp_isPivot", source.IsPivot.toString())
        props.setProperty("document.dynamic.userdefined.ddp_transpose", source.Transpose.toString())
        props.setProperty("document.dynamic.userdefined.ddp_displayHeaders", source.DisplayHeaders.toString())
        props.setProperty("document.dynamic.userdefined.ddp_displayHeadersOnSide", source.DisplayHeadersOnSide.toString())

        is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(outData)).getBytes("UTF-8"));
        dataContext.storeStream(is, props);
    }
    // println masterValuesMap
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
