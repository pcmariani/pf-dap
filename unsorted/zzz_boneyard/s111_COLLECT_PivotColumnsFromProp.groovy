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
    def root = new JsonSlurper().parseText(requestJson)

    props.setProperty("document.dynamic.userdefined.ddp_pivotOnColsJson", JsonOutput.toJson(root.PivotOnColumns))
    props.setProperty("document.dynamic.userdefined.ddp_pivotGroupByColsJson", JsonOutput.toJson(root.PivotGroupByColumns))
    props.setProperty("document.dynamic.userdefined.ddp_pivotDataColumn", root.PivotDataColumn)
    props.setProperty("document.dynamic.userdefined.ddp_pivotTopLeftCornerOpt", root.PivotTopLeftCornerOpt)

    is = new ByteArrayInputStream(data.getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

