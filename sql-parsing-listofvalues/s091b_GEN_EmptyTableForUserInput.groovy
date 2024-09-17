import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();

// def OFS = "|^|"  // Output Field Separater

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    /* INPUTS */

    def userInputsJson = props.getProperty("document.dynamic.userdefined.ddp_UserInputs")
    def userInputs = userInputsJson ? new JsonSlurper().parseText(userInputsJson).Records : []
    // println prettyJson(userInputs)
    def reportContentItemJson = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem")
    def reportContentItem = reportContentItemJson ? new JsonSlurper().parseText(reportContentItemJson).Records[0] : []
    // println prettyJson(reportContentItem)

    // Incoming Document is SourceSqlQuery
    def sourceSqlQueryRoot = new JsonSlurper().parse(is).Records[0]
    // println prettyJson(sourceSqlQueryRoot)
    def paramUserInputMap = sourceSqlQueryRoot.ParamUserInputMap
    // println prettyJson(paramUserInputMap)
    def sqlMetadata = sourceSqlQueryRoot.SqlMetadata
    // println prettyJson(sqlMetadata)
    def params = sqlMetadata.Params
    // println prettyJson(params)
    def fromTablesArr = sqlMetadata.FromTables
    // println prettyJson(fromTablesArr)
    def unionOperator = sqlMetadata.UnionOperator
    // println unionOperator
    def hasUnion = unionOperator != null
    // println hasUnion

    /* LOGIC */

    Set paramUserInputsArr = []

    fromTablesArr.eachWithIndex { fromTable, uIndex ->
        // println "\n------------------------------  UNION INDEX: $uIndex  -------------------------------"
        params.findAll{ it.UnionIndex == uIndex }.each { param ->
            // println "param: " + prettyJson(param)
            if (!param.Duplicate) {
                def userInputId, userInput, paramValue
                if (!param.HardCodedValue) {
                    userInputId = paramUserInputMap.find{ it.ParamName == (hasUnion ? "Statement#${uIndex}_" : "") + param.Column }?.UserInputId
                    // println userInputId
                    userInput = userInputs.find{ it.UserInputId == userInputId }
                    // println userInput
                    def globalVariableName = userInput?.GlobalVariableName?.replaceAll(" ","_")
                    // println globalVariableName
                    if (globalVariableName) {
                        paramValue = ExecutionUtil.getDynamicProcessProperty("DPP_" + globalVariableName)
                        if (!paramValue) paramValue = ExecutionUtil.getDynamicProcessProperty("dpp_" + globalVariableName)
                        if (!paramValue) paramValue = ExecutionUtil.getDynamicProcessProperty(globalVariableName)
                        if (!paramValue) paramValue = reportContentItem.SampleGlobalVariables.find{it.Name.replaceAll(" ","_") == globalVariableName}?.Value
                        if (paramValue) paramValue = "$paramValue"
                    }
                    // println paramValue
                    paramUserInputsArr << [
                        UserInputName: userInput.UserInputName,
                        Value: paramValue,
                        UserInputId: userInput.UserInputId,
                        Operator: param.Operator
                    ]
                }
            }
        }
        // println "paramUserInputsArr: " + prettyJson(paramUserInputsArr)
    }

    def paramUserInputsValsArr = paramUserInputsArr.collect{ it.values() as ArrayList }.transpose()
    // println paramUserInputsValsArr

    // the index numbers correspond to the index of the items paramUserInputsArr
    def result = [
        "ListOfValues": paramUserInputsValsArr[0..1].collect{
            it.withIndex().collectEntries{ item, j ->
                ["Col$j": item ?: ""]
            }
        },
        "UserInputIdsString": paramUserInputsValsArr[2].join(","),
        "Multiselect": paramUserInputsArr.Operator.contains("IN"),
        "Operators": paramUserInputsValsArr[3].join(",")
    ]

    is = new ByteArrayInputStream(prettyJson(result).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
