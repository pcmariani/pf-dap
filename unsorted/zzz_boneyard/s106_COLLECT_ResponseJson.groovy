import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def responseJson = props.getProperty("document.dynamic.userdefined.ddp_responseJson")
    def responseJsonRoot = new JsonSlurper().parseText(responseJson)
    // println responseJsonRoot
    def tableRowsRoot = new JsonSlurper().parse(is)
    // println tableRowsRoot
    responseJsonRoot.TableRows = tableRowsRoot

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(responseJsonRoot)).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

