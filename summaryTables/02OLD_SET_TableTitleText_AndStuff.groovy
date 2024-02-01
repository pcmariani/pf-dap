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

    def tableInstanceRoot = new JsonSlurper().parse(is).Records[0]
    def userInputValues = tableInstanceRoot.UserInputValues
    // println userInputValues
    def userInputsJson = props.getProperty("document.dynamic.userdefined.ddp_UserInputs")
    def userInputs = new JsonSlurper().parseText(userInputsJson).Records
    // println userInputs
    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = new JsonSlurper().parseText(sourcesJson).Records
    // println prettyJson(sources)
    def reportContentItemJson = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem")
    def reportContentItem = reportContentItemJson ? new JsonSlurper().parseText(reportContentItemJson).Records[0] : []
    // println reportContentItem
    def virtualColumnsJson = props.getProperty("document.dynamic.userdefined.ddp_VirtualColumns")
    def virtualColumns = virtualColumnsJson ? new JsonSlurper().parseText(virtualColumnsJson).Records : []
    // println prettyJson(virtualColumns)


    /* LOGIC */

    def tempValuesPerSourcePerTableType = [:]

    sources.sort { ["Data Table","Summary Table","Calculations Table"].indexOf(it.ResultTableType) }.each { source ->

        // println "\n---------- $source.ResultTableType -----------\n"

        def valuesMapArr = []

        source.ParamUserInputMap.findAll{it.UserInputId != null}.each { param ->
            def valuesMap = [:]

            def userInput = userInputs.find{it.UserInputId == param.UserInputId}
            def userInputName = userInput.UserInputName
            def value

            valuesMap.'UserInputId' = userInput.UserInputId
            valuesMap.'UserInputName' = userInputName
            valuesMap.'ParamName' = param.ParamName

            if (source.ResultTableType =~ /(?i)data/) {
                valuesMap.'PivotConfig' = 
                    source.PivotOnColumns.findAll { it.Column == param.ParamName } ? "PivotOn" :
                    source.PivotGroupByColumns.findAll { it.Column == param.ParamName } ? "GroupBy" :
                    null
            }
            else if (source.ResultTableType =~ /(?i)summary/) {
                valuesMap.'PivotConfig' = tempValuesPerSourcePerTableType.'Data Table'.find { 
                    it.UserInputId == userInput.UserInputId
                }?.PivotConfig
            }

            value = userInputValues.find{it.UserInputId == param.UserInputId}?.UserInputValue

            if (!value) {
                def globalVariableName = userInput.GlobalVariableName?.replaceAll(" ","_")
                if (globalVariableName) {
                    value = ExecutionUtil.getDynamicProcessProperty("DPP_" + globalVariableName)
                    if (!value) value = ExecutionUtil.getDynamicProcessProperty("dpp_" + globalVariableName)
                    if (!value) value = ExecutionUtil.getDynamicProcessProperty(globalVariableName)
                    if (!value) value = reportContentItem.SampleGlobalVariables.find{it.Name.replaceAll(" ","_") == globalVariableName}?.Value
                }
            }
            if (!value) {
                println "UH OH"
            }

            valuesMap.'Value' = value
            valuesMap.'MultiSelect' = value.contains(",") ? true : false

            valuesMapArr << valuesMap
        }

        if (virtualColumns) {
            virtualColumns.each { vc ->
                def valuesMap = [:]
                def virtualColumnLabel = vc.ColumnLabel
                def virtualColumnValue = vc.VirtualColumnRows?.find{
                    it.TableInstanceId == tableInstanceRoot.TableInstanceId
                }?.Value
                if (virtualColumnValue) {
                    valuesMap.'UserInputId' = "VIRTUAL"
                    valuesMap.'UserInputName' = virtualColumnLabel
                    valuesMap.'ParamName' = virtualColumnLabel
                    valuesMap.'PivotConfig' = "none"
                    valuesMap.'Value' = virtualColumnValue
                    valuesMap.'MultiSelect' = value.contains(",") ? true : false
                }
                valuesMapArr << valuesMap
            }
        }

        // valuesMapArr.each { println it }
        // println valuesMapArr.value.join(";")
        tempValuesPerSourcePerTableType."$source.ResultTableType" = valuesMapArr
        // tempValuesPerSourcePerTableType.each { println it ; println "----"}


        /* OUTPUT */

        def outData = [Records: [source]]

        props.setProperty("document.dynamic.userdefined.ddp_Sources", JsonOutput.toJson([Records:[source]]))
        props.setProperty("document.dynamic.userdefined.ddp_TableInstanceId", tableInstanceRoot.TableInstanceId.toString())
        props.setProperty("document.dynamic.userdefined.ddp_sqlParamValues", valuesMapArr.Value.join(";"))
        props.setProperty("document.dynamic.userdefined.ddp_sqlParamUserInputValuesJson", prettyJson(valuesMapArr))

        is = new ByteArrayInputStream(prettyJson(outData).getBytes("UTF-8"));
        dataContext.storeStream(is, props);
    }
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
