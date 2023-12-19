import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger();

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def root = new XmlParser().parse(is)
    def script = root.'bns:object'.ProcessingScript.script.text()

    is = new ByteArrayInputStream(script.getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

