import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()
def IFS = /\|\^\|/  // Input Field Separater

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def outRowsArr = []

    def sqlColumnNames = props.getProperty("document.dynamic.userdefined.ddp_sqlColumnNames")
    // println sqlColumnNames

    ArrayList columnNamesArr
    if (sqlColumnNames) {
        columnNamesArr = sqlColumnNames.split(IFS)
        // println columnNamesArr
        outRowsArr << columnNamesArr.withIndex().collectEntries{ item, j -> ["col$j":item] }
    }
    // println outRowsArr

    def reader = new BufferedReader(new InputStreamReader(is))

    while ((line = reader.readLine()) != null ) {
        ArrayList lineArr =  line.split(IFS)
        outRowsArr << lineArr.withIndex().collectEntries{ item, j -> ["col$j":item] }
    }
    // println outRowsArr

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(outRowsArr)).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}
