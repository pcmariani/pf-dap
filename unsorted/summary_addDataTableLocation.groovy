import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

def OFS = "|^|"  // Output Field Separater

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = new JsonSlurper().parseText(sourcesJson).Records

    if (sources.ResultTableType.join(" ") =~ /(?i).*Summary.*/)
        ExecutionUtil.setDynamicProcessProperty("DPP_HasSummarySource", "true", false);

    dataContext.storeStream(is, props);

}
