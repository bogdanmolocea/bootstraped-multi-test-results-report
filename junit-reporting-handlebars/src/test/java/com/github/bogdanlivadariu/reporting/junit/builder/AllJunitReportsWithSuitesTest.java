package com.github.bogdanlivadariu.reporting.junit.builder;

import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.junit.Before;
import org.junit.Test;

public class AllJunitReportsWithSuitesTest {
    private List<String> xmlReports;

    private String reportPath = this.getClass().getClassLoader().getResource("valid-report-2.xml").getPath();

    private JUnitReportBuilder builder;

    private AllJUnitReports reports;

    @Before
    public void setUp() throws FileNotFoundException, JAXBException {
        xmlReports = new ArrayList<>();
        xmlReports.add(reportPath);
        builder = new JUnitReportBuilder(xmlReports, "out");
        reports = new AllJUnitReports("title", builder.getProcessedTestSuites());
    }

    @Test
    public void restSuitesSizeTest() throws FileNotFoundException, JAXBException {
        assertEquals("reports count is not right",
            reports.getAllTestSuites().size(), 2);
        assertEquals(reports.getSuitesCount(), 2);
        assertEquals(reports.getTotalErrors(), 0);
    }

    @Test
    public void pageTitleTest() {
        assertEquals(reports.getPageTitle(), "title");
    }

    @Test
    public void totalErrorsTest() {
        assertEquals(reports.getSuitesCount(), 2);
    }

    @Test
    public void totalFailuresTest() {
        assertEquals(reports.getTotalFailures(), 2);
    }

    @Test
    public void totalSkippedTest() {
        assertEquals(reports.getTotalSkipped(), 0);
    }

    @Test
    public void totalTestsTest() {
        assertEquals(reports.getTotalTests(), 8);
    }

    @Test
    public void totalTimeTest() {
        assertEquals(reports.getTotalTime(), "8.1");
    }
}
