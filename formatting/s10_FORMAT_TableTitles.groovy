import java.util.Properties;
import java.io.InputStream;
import groovy.xml.XmlUtil
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

def sectionNumber = ExecutionUtil.getDynamicProcessProperty("DPP_SectionNumber") ?: "0.0.0.0.0"
//println sectionNumber
def tot_prefixOption = ExecutionUtil.getDynamicProcessProperty("dpp_figure_no_style") ?: "Prefix"
//println tot_prefixOption
def tot_text_to_prepend = (ExecutionUtil.getDynamicProcessProperty("DPP_TableOfTables_TextToPrepend") ?: "Table" ) - ~/\s*$/
//println tot_text_to_prepend

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def root = new XmlSlurper().parseText(is.text.replaceAll(/&/,"&amp;"))

    // if the table title contains the section prefix, remove it
    def tableTitle = root.h3.toString().replaceAll(/^.*-\d{1,2}\.?\s*/,"")

    // if we want the section prefix, add it
    if (tot_prefixOption =~ /(?i)section prefix/) {
        tableTitle = root.h3.@sectionPrefix.toString() + ". " + tableTitle
    }
    //println tableTitle

    root.h3 = tableTitle

    String outData = XmlUtil.serialize(root) - ~/^.*\?>/
    is = new ByteArrayInputStream(outData.getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}


