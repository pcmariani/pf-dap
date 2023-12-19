import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def root = new JsonSlurper().parse(is)
    def paramsString = root.Params.Value.join("; ")

    props.setProperty("document.dynamic.userdefined.ddp_sqlParamString", paramsString)

    is = new ByteArrayInputStream(paramsString.getBytes("UTF-8"))
    dataContext.storeStream(is, props);
}
