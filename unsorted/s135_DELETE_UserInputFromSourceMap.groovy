import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def requestor = props.getProperty("document.dynamic.userdefined.ddp_requestor")
    int userInputId = (props.getProperty("document.dynamic.userdefined.ddp_UserInputId") ?: "-1" ) as int

    def root = new JsonSlurper().parse(is)
    root.Records[0].ParamUserInputMap.find{ it.UserInputId == userInputId }.UserInputId = null
    root.Requestor = requestor

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(root)).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}
