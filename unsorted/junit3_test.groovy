// CalculatorTest.groovy
import junit.framework.TestCase

class CalculatorTest extends TestCase {
  ScriptRunner sc
//     Calculator calculator
//
    protected void setUp() {
        sc = new ScriptRunner()
        println sc.run(os, "s120_CREATE_NewPivotedDataConfigs.groovy", "tests/ba3.dat", "tests/ba3.properties", options)
    }
//
    protected void tearDown() {
        // Clean up resources if needed
    }
//
    // Test the add method
    void testAdd() {
      assertNotNull("is not null", sc)
        // assertEquals(5, calculator.add(2, 3))
        // assertEquals(-1, calculator.add(-2, 1))
        // assertEquals(0, calculator.add(0, 0))
    }
}
