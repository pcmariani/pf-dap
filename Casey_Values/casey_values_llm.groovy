import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper;

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def json = new JsonSlurper().parse(is);
    json.params.each { param ->
        def valueList = param.value_list.collect { it.value }.join("---")
        param.value_list_string = valueList
    }

    def newJson = new groovy.json.JsonBuilder(json).toPrettyString()
    is = new ByteArrayInputStream(newJson.getBytes("UTF-8"))

    dataContext.storeStream(is, props);
}
