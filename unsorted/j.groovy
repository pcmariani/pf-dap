import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def newRoot = [:]
    new JsonSlurper().parse(is).each { k,v ->
        if (v instanceof List) newRoot[k] = v
        else newRoot[k] = [v]
    }

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(newRoot)).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}
