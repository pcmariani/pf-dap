import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper;
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

for ( int i = 0; i < dataContext.getDataCount(); i++ ) {

def patternGrid = ExecutionUtil.getDynamicProcessProperty("dpp_pattern_grid")
def patternEntityId = ExecutionUtil.getDynamicProcessProperty("dpp_pattern_entity_id")

InputStream is = dataContext.getStream(i);
Properties props = dataContext.getProperties(i);

    def userInputsJson = props.getProperty("document.dynamic.userdefined.ddp_UserInputs")
    def userInputs = userInputsJson ? new JsonSlurper().parseText(userInputsJson).Records : []
    // println prettyJson(userInputs)

    def newGlobalVars = userInputs.GlobalVariableName.findAll{ it != null }
    println newGlobalVars

    def outData = prettyJson([
      gr_id: patternGrid,
      entity_id: patternEntityId,
      variables: [ newGlobalVars.collect { [search_text: it] } ]
    ])

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}


private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
