import java.util.Properties;
import java.io.InputStream;
import groovy.xml.XmlUtil
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()
def IFS = /\|\^\|/  // Input Field Separater

// def pivotedDataConfigsJson = ExecutionUtil.getDynamicProcessProperty("DPP_PivotedDataConfigs")

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    Boolean isPivot = (props.getProperty("document.dynamic.userdefined.ddp_isPivot") ?: "true").toBoolean()
    // println "#DEBUG isPivot: " + isPivot

    Boolean transpose = (props.getProperty("document.dynamic.userdefined.ddp_transpose") ?: "false").toBoolean()
    // println "#DEBUG transpose: " + transpose

    /* LOGIC */

    println "#DEBUG transpose: " + transpose
    if (isPivot && transpose) {

        def tableGroup = new XmlParser().parse(is)
        def table = tableGroup.table[0]

        def tableArr = []
        table.tr.each { tr ->
            // println "\n------- tr ---------\n"
            def rowArr = []
            tr.children().each { trChild ->
                // println trChild.value()
                rowArr << trChild
            }
            // println rowArr
            tableArr << rowArr
        }

        // tableArr.each { println it ; println ""}
        // tableArr.transpose().each { println it ; println ""}

        def newTableNode = new NodeBuilder().table(table.attributes())
        tableGroup.append(newTableNode)

        tableArr.transpose().each { tr ->
        
            def newTrNode = new NodeBuilder().tr() {
                tr.each { trChild ->
                    // println trChild
                    // def childNode = tr.children()[item.ColumnIndex]
                    "${trChild.name()}"(trChild.attributes(), "${trChild.text()}")
                }
            }
            newTableNode.append(newTrNode)
        }
        table.replaceNode{}

        def outData = groovy.xml.XmlUtil.serialize(tableGroup).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim()
        is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    }

    dataContext.storeStream(is, props);
}
 
