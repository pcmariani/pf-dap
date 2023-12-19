import java.util.Properties;
import java.io.InputStream;
import java.text.DecimalFormat;
import groovy.json.JsonSlurper
import com.boomi.execution.ExecutionUtil;
logger = ExecutionUtil.getBaseLogger()

def NEWLINE = System.lineSeparator()
def IFS = /\|\^\|/  // Input Field Separator
def OFS = "|^|"     // Output Field Separator
// def OFS = "\t"     // Output Field Separator

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    /* --- get property values --- */
    Boolean transpose = (props.getProperty("document.dynamic.userdefined.ddp_transpose") ?: "false").toBoolean()

    def dataArr = []
    def reader = new BufferedReader(new InputStreamReader(is))
    while ((line = reader.readLine()) != null ) {
        def lineArr = line.split(/\s*$IFS\s*/)
        if (lineArr) dataArr << lineArr
    }
    dataArr.each {println it}
    println dataArr[0..-1].transpose()

    if (transpose) dataArr = dataArr.transpose()

    def outData = new StringBuffer()
    dataArr.each {outData.append(it.join(OFS) + NEWLINE)}

    // int numHeaderRows = !transpose ? pivotOnColsArr.size() : groupByColsArr.size()
    // props.setProperty("document.dynamic.userdefined.ddp_numHeaderRows", numHeaderRows.toString())

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}
