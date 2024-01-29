import java.util.Properties;
import java.io.InputStream;
// import groovy.util.XmlParser;
import com.boomi.execution.ExecutionUtil;

for ( int i = 0; i < dataContext.getDataCount(); i++ ) {

InputStream is = dataContext.getStream(i);
Properties props = dataContext.getProperties(i);

    def root = new XmlParser().parse(is)

    def varsArr = root.Record.Fields.'*'.variables.variable
    varsArr.each {
        // println it.search_text.text() + it.replace_text.text()
        ExecutionUtil.setDynamicProcessProperty("DPP_" + it.search_text.text().replaceAll(" ","_"), it.replace_text.text(), false);
    }

    def outData = groovy.xml.XmlUtil.serialize(root).replaceAll("\\<\\?xml(.+?)\\?\\>", "").trim()
    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}


