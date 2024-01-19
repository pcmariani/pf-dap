import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;
logger = ExecutionUtil.getBaseLogger()

def NEWLINE = System.lineSeparator()
def IFS = /\|\^\|/  // Input Field Separator
def OFS = "|^|"     // Output Field Separator

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def reader = new BufferedReader(new InputStreamReader(is))
    def outData = new StringBuffer()

    // int j = 1
    while ((line = reader.readLine()) != null ) {
        // outData.append(line + NEWLINE)
        def lineArr = line.split(/\s*$IFS\s*/)

        def oldLabelsClean = lineArr[2].replaceFirst("\"","").replaceFirst("\"\$","").replaceAll("\"\"","\"")
        // println j + " " + new JsonSlurper().parseText(oldLabelsClean)
        // j++

        def oldLabelsArr = new JsonSlurper().parseText(oldLabelsClean).Label
        def keysArr = lineArr[1].split("___")
        def numKeys = keysArr.size()
        def newLabelsArr = []

        (0..numKeys-1).each { n ->
          if (oldLabelsArr[n]) {
            newLabelsArr << oldLabelsArr[n]
          } else {
            newLabelsArr << ""
          }
        }

        def id = lineArr[0]
        def key = keysArr.join(OFS)
        def label = newLabelsArr.join(OFS)
        outData.append("UPDATE DynamicTable_PivotedDataConfig")
        outData.append("  SET ColumnKey = \'" + key + "\'")
        outData.append(", ColumnLabels = \'" + label + "\'")
        outData.append("  WHERE PivotedDataConfigId = \'" + id + "\'")
        outData.append(";" + NEWLINE)

        // outData.append(lineArr[0] + "\t" + keysArr.join(OFS) + "\t" + newLabelsArr.join(OFS) + NEWLINE)
        // println keysArr.join(OFS)
        // println newLabelsArr.join(OFS)

    }

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
