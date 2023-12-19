import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();


for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    /* INPUTS */

    def root = new JsonSlurper().parse(is)

    /* LOGIC */

    def report = [
        "Action": root.Action,
        "Requestor": root.Requestor,
        "ReportId": root.ReportId,
        "GdmsProperties": root.collect{ k,v->
            ["property":k, "value":v]
        }.findAll{!(it.property in ["Action","Requestor","ReportId","Status","Message"])}

    ]
    // println prettyJson(report)

    /* OUTPUT */

    // is = new ByteArrayInputStream("".getBytes("UTF-8"));
    is = new ByteArrayInputStream(prettyJson(report).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
