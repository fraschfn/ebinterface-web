package at.ebinterface.validation.web;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.wicket.Page;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.protocol.http.WebApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.string.StringHelper;

import at.ebinterface.validation.validator.EbInterfaceValidator;
import at.ebinterface.validation.web.pages.LabsPage;
import at.ebinterface.validation.web.pages.ServicePage;
import at.ebinterface.validation.web.pages.StartPage;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;


/**
 * The application class for the ebInterface validation application
 *
 * @author pl
 */
public class ValidationApplication extends WebApplication {


  /**
   * Defines whether the application shall be started in develop or deployment mode
   */
  private static RuntimeConfigurationType CONFIGURATION_TYPE = RuntimeConfigurationType.DEPLOYMENT;

  private static final Logger LOG = LoggerFactory.getLogger(ValidationApplication.class.getName());

  private static final String APP_PATH;


  static {
    //Set the manual keystore, otherwise the RTR certificate is not trusted
    try {
      final URL url = ValidationApplication.class.getResource("/keystore.jks");
      LOG.info("Setting key store reference to {}", url.getPath());
      System.setProperty("javax.net.ssl.trustStore", url.getPath());
      System.setProperty("javax.net.ssl.trustStorePassword", "");

    } catch (final Exception e1) {
      throw new RuntimeException("Error while reading SSL Keystore. Unable to proceed.", e1);
    }

    APP_PATH = System.getenv("APPLICATION_PATH");
    if (StringHelper.hasNoText(APP_PATH))
      LOG.debug("APPLICATION_PATH not set, always returning default homepage");
  }

  @Override
  protected void init() {
    super.init();

    try {
      LOG.info("Compiling JasperReport template for ebInterface");
      final JasperDesign
          jrDesign =
          JRXmlLoader.load(
              this.getClass().getClassLoader().getResourceAsStream("reports/ebInterface.jrxml"));
      final JasperReport jrReport = JasperCompileManager.compileReport(jrDesign);
      setMetaData(Constants.METADATAKEY_EBINTERFACE_JRTEMPLATE, jrReport);
      LOG.info("JasperReport template for ebInterface is now stored in application context.");
    } catch (final Exception ex) {
      LOG.error("Could not load ebInterface PDF template!", ex);
    }

    try {
      LOG.info("Compiling JasperReport template for ZUGFeRD");
      final JasperDesign
          jrDesign =
          JRXmlLoader
              .load(this.getClass().getClassLoader().getResourceAsStream("reports/ZUGFeRD.jrxml"));
      final JasperReport jrReport = JasperCompileManager.compileReport(jrDesign);
      setMetaData(Constants.METADATAKEY_ZUGFERD_JRTEMPLATE, jrReport);
      LOG.info("JasperReport template for ZUGFeRD is now stored in application context.");
    } catch (final Exception ex) {
      LOG.error("Could not load ZUGFeRD PDF template!", ex);
    }

    LOG.info("Initializing XML schema validator for ebInterface");
    final EbInterfaceValidator validator = new EbInterfaceValidator();
    setMetaData(Constants.METADATAKEY_EBINTERFACE_XMLSCHEMAVALIDATOR, validator);
    LOG.info("XML schema validator for ebInterface is now stored in application context.");

    LOG.info("Initializing XML schema validator for ZUGFeRD");
    final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    Schema schema = null;
    try {
      schema = factory.newSchema(new StreamSource(new File(
          ValidationApplication.class
              .getResource("/zugferd1p0/ZUGFeRD1p0.xsd").toURI())));
    } catch (final Exception e) {
      LOG.error(e.getMessage(), e);
    }
    setMetaData(Constants.METADATAKEY_ZUGFERD_XMLSCHEMA, schema);
    LOG.info("XML schema validator for ZUGFeRD is now stored in application context.");

    LOG.info("Initializing schematron transformer for ZUGFeRD");
    try {
      final Source xsl = new StreamSource(new File(
          ValidationApplication.class
              .getResource("/zugferd1p0/ZUGFeRD_1p0-compiled.xsl").toURI()));
      final TransformerFactory transFactory = TransformerFactory.newInstance();
      final Templates templates = transFactory.newTemplates(xsl);
      setMetaData(Constants.METADATAKEY_ZUGFERD_SCHEMATRONTEMPLATE, templates);
    } catch (final Exception e) {
      LOG.error(e.getMessage(), e);
    }
    LOG.info("schematron transformer for ZUGFeRD is now stored in application context.");

    getMarkupSettings().setDefaultMarkupEncoding(StandardCharsets.UTF_8.name ());
    getRequestCycleSettings().setResponseRequestEncoding(StandardCharsets.UTF_8.name ());

    mountPage("/service", ServicePage.class);
    mountPage("/labs", LabsPage.class);
  }

  /**
   * Returns the configuration type (develop or deployment)
   */
  @Override
  public RuntimeConfigurationType getConfigurationType() {
    return CONFIGURATION_TYPE;
  }


  /**
   * Returns the home page
   */
  @Override
  public Class<? extends Page> getHomePage() {
    // https://gitlab.ecosio.com/misc/austriapro/issues/11
    Class<? extends Page> homePage = StartPage.class;

    if (StringHelper.hasText(APP_PATH))
      switch (APP_PATH) {
        case "service":
          LOG.debug("Homepage is ServicePage");
          homePage = ServicePage.class;
          break;
        case "labs":
          LOG.debug("Homepage is LabsPage");
          homePage = LabsPage.class;
          break;
        default:
          LOG.info("Could not determine type of landing page, using startpage");
      }
    return homePage;
  }
}
