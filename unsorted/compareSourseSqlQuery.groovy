import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def sourceSqlQueryQueriedJson = new JsonSlurper().parse(is)
    def sourceSqlQueryQueried = sourceSqlQueryQueriedJson.Records[0]
    // println sourceSqlQueryQueried
    def sourceSqlQueryUpdateRequestJson = props.getProperty("document.dynamic.userdefined.ddp_SourceSqlQuery_UpdateRequest")
    def sourceSqlQueryUpdateRequest = sourceSqlQueryUpdateRequestJson ? new JsonSlurper().parseText(sourceSqlQueryUpdateRequestJson).Records[0] : []
    // println prettyJson(sourceSqlQueryUpdateRequest)

    if (sourceSqlQueryQueried.PivotOnColumns == sourceSqlQueryUpdateRequest.PivotOnColumns) {
        props.setProperty("document.dynamic.userdefined.ddp_PivotOnColumnConfigsAreEqual", "true")
    } else {
        props.setProperty("document.dynamic.userdefined.ddp_PivotOnColumnConfigsAreEqual", "false")
    }

    if (sourceSqlQueryQueried.PivotGroupByColumns == sourceSqlQueryUpdateRequest.PivotGroupByColumns) {
        props.setProperty("document.dynamic.userdefined.ddp_GroupByColumnConfigsAreEqual", "true")
    } else {
        props.setProperty("document.dynamic.userdefined.ddp_GroupByColumnConfigsAreEqual", "false")
    }

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(sourceSqlQueryQueriedJson)).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}
