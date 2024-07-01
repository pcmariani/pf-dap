import java.util.Properties;
import java.io.InputStream;
import groovy.xml.XmlUtil
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

def tot_prefixOption = ExecutionUtil.getDynamicProcessProperty("dpp_table_no_style") ?: "No Prefix"
//println tot_prefixOption
def tot_text_to_prepend = (ExecutionUtil.getDynamicProcessProperty("DPP_TableOfTables_TextToPrepend") ?: "Table" ) - ~/\s*$/
//println tot_text_to_prepend

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def data = is.text.replaceAll(/&/,"&amp;")
    def root = new XmlSlurper().parseText("<root>$data</root>")
    def tablegroups = root.tablegroup

    tablegroups.each { tablegroup ->
        //println tablegroup.h3

        // if the table title contains the section prefix, remove it
        def tableTitle = tablegroup.h3.toString().replaceAll(/^.*-\d{1,2}\.?\s*/,"")

        // if we want the section prefix, add it
        if (tot_prefixOption =~ /(?i)section prefix/) {
            tableTitle = tablegroup.h3.@sectionprefix.toString() + " " + tableTitle
        }
        //println tableTitle

        tablegroup.h3 = tableTitle
    }

    String outData = (XmlUtil.serialize(root) -~/^.*\?>/).replaceAll(/<\/?root>/, "").trim()

    //String outData = ""
    is = new ByteArrayInputStream(outData.getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}


