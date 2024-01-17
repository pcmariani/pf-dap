import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def root = new JsonSlurper().parse(is)
    root.params.each { 
      it.value_list_string = it.value_list.collect{ it.value }.join("---") 
    } 

    is = new ByteArrayInputStream(prettyJson(root).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
