import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def data = is.text
    def requestJson = props.getProperty("document.dynamic.userdefined.ddp_requestJson")
    def root

    if (requestJson) {
        root = new JsonSlurper().parseText(requestJson)
    }
    else {
        root = new JsonSlurper().parseText(data)
    }

    def pivotOnColumns
    def pivotGroupByColumns

    if (root.Records) {
        pivotOnColumns = root.Records[0].PivotOnColumns
        pivotGroupByColumns = root.Records[0].PivotGroupByColumns
    }
    else {
        pivotOnColumns = root.PivotOnColumns
        pivotGroupByColumns = root.PivotGroupByColumns
    }

    props.setProperty("document.dynamic.userdefined.ddp_PivotOnColumns", JsonOutput.toJson(pivotOnColumns))
    props.setProperty("document.dynamic.userdefined.ddp_PivotGroupByColumns", JsonOutput.toJson(pivotGroupByColumns))

    is = new ByteArrayInputStream(data.getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

