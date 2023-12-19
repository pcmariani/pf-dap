import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def userInputsJson = props.getProperty("document.dynamic.userdefined.ddp_UserInputs")
    def userInputs = userInputsJson ? new JsonSlurper().parseText(userInputsJson).Records : []
    // println prettyJson(userInputs)

    def sourceSqlQueryJson = props.getProperty("document.dynamic.userdefined.ddp_SourceSqlQuery")
    def sourceSqlQuery = sourceSqlQueryJson ? new JsonSlurper().parseText(sourceSqlQueryJson).Records[0] : []
    // println prettyJson(sourceSqlQuery)
    def paramUserInputMap = sourceSqlQuery.ParamUserInputMap
    // println prettyJson(paramUserInputMap)

    def sqlMetadataJsonRoot = new JsonSlurper().parse(is)
    // println prettyJson(sqlMetadataJsonRoot)

    sqlMetadataJsonRoot.Params.each { param ->
        param.UserInputId = paramUserInputMap.find{ it.ParamName == param.Column }?.UserInputId
        param.UserInputName = userInputs.find{ it.UserInputId == param.UserInputId }?.UserInputName
    }

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(sqlMetadataJsonRoot)).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

// private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
