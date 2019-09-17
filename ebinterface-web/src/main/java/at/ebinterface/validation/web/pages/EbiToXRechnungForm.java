package at.ebinterface.validation.web.pages;

import java.io.IOException;
import java.util.Locale;

import javax.xml.bind.ValidationEventHandler;

import org.apache.wicket.feedback.ContainerFeedbackMessageFilter;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.error.IError;
import com.helger.commons.error.list.ErrorList;
import com.helger.commons.io.stream.StreamHelper;
import com.helger.commons.string.StringHelper;
import com.helger.ebinterface.EEbInterfaceVersion;
import com.helger.ebinterface.builder.EbInterfaceReader;
import com.helger.ebinterface.v40.Ebi40InvoiceType;
import com.helger.ebinterface.v41.Ebi41InvoiceType;
import com.helger.ebinterface.v42.Ebi42InvoiceType;
import com.helger.ebinterface.v43.Ebi43InvoiceType;
import com.helger.ebinterface.v50.Ebi50InvoiceType;
import com.helger.ebinterface.xrechnung.to.ubl.EbInterface40ToXRechnungUBLConverter;
import com.helger.ebinterface.xrechnung.to.ubl.EbInterface41ToXRechnungUBLConverter;
import com.helger.ebinterface.xrechnung.to.ubl.EbInterface42ToXRechnungUBLConverter;
import com.helger.ebinterface.xrechnung.to.ubl.EbInterface43ToXRechnungUBLConverter;
import com.helger.ebinterface.xrechnung.to.ubl.EbInterface50ToXRechnungUBLConverter;
import com.helger.jaxb.validation.WrappedCollectingValidationEventHandler;
import com.helger.ubl21.UBL21Writer;
import com.helger.xml.sax.InputSourceFactory;

import at.ebinterface.validation.exception.NamespaceUnknownException;
import at.ebinterface.validation.parser.CustomParser;
import at.ebinterface.validation.web.Constants;
import at.ebinterface.validation.web.pages.resultpages.ResultPageXRechnung;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;

/**
 * Form for converting ebInterface to XRechnung Invoice
 *
 * @author Philip Helger
 */
final class EbiToXRechnungForm extends Form <Object>
{
  private static final Logger LOG = LoggerFactory.getLogger (EbiToXRechnungForm.class);
  private static final ICommonsList <EEbInterfaceVersion> POSSIBLE_EBI_VERSIONS = new CommonsArrayList <> (EEbInterfaceVersion.V40,
                                                                                                           EEbInterfaceVersion.V41,
                                                                                                           EEbInterfaceVersion.V42,
                                                                                                           EEbInterfaceVersion.V43,
                                                                                                           EEbInterfaceVersion.V50);

  /**
   * Panel for providing feedback in case of erroneous input
   */
  private FeedbackPanel feedbackPanel;

  /**
   * Upload field for the ebInterface instance
   */
  private FileUploadField fileUploadField;

  /**
   * Was the link called from the start page or from the /labs page?
   */
  private final boolean fromStartPage;

  public EbiToXRechnungForm (final String id, boolean fromStartPage)
  {
    super (id);
    this.fromStartPage = fromStartPage;

    // Add a feedback panel
    feedbackPanel = new FeedbackPanel ("feedback", new ContainerFeedbackMessageFilter (this));
    feedbackPanel.setVisible (false);
    add (feedbackPanel);

    // Add the file upload field
    fileUploadField = new FileUploadField ("fileInputEbiToXRechnung");
    fileUploadField.setRequired (true);
    add (fileUploadField);

    // Add a submit button
    add (new SubmitLink ("convertEbiToXRechnung"));
  }

  @Override
  protected void onSubmit ()
  {
    feedbackPanel.setVisible (false);

    // Get the file input
    final FileUpload upload = fileUploadField.getFileUpload ();
    byte [] uploadedData = null;
    try
    {
      uploadedData = StreamHelper.getAllBytes (upload.getInputStream ());
    }
    catch (final IOException e)
    {
      LOG.error ("Die hochgeladene Datei kann nicht verarbeitet werden.", e);
    }

    // Step 1 - determine the correct ebInterface version
    EEbInterfaceVersion eVersion;
    try
    {
      eVersion = CustomParser.INSTANCE.getEbInterfaceDetails (InputSourceFactory.create (uploadedData)).getVersion ();
    }
    catch (final NamespaceUnknownException e1)
    {
      eVersion = null;
    }

    if (eVersion == null)
    {
      error ("Die hochgeladene Datei konnte nicht als ebInterface-Datei interpretiert werden.");
      onError ();
      return;
    }

    if (!POSSIBLE_EBI_VERSIONS.contains (eVersion))
    {
      error ("Es können nur ebInterface-Dateien in den folgenden Versionen konvertiert werden: " +
             StringHelper.getImplodedMapped (", ",
                                             POSSIBLE_EBI_VERSIONS,
                                             x -> x.getVersion ().getAsString (false, true)));
      onError ();
      return;
    }

    LOG.info ("Parsing upload as ebInterface " + eVersion.getVersion ().getAsString ());

    // Parse ebInterface against XSD
    final ErrorList aErrorListEbi = new ErrorList ();
    final ValidationEventHandler aValidationHdl = new WrappedCollectingValidationEventHandler (aErrorListEbi);
    final Object aParsedInvoice;
    switch (eVersion)
    {
      case V40:
        aParsedInvoice = EbInterfaceReader.ebInterface40 ()
                                          .setValidationEventHandler (aValidationHdl)
                                          .read (uploadedData);
        break;
      case V41:
        aParsedInvoice = EbInterfaceReader.ebInterface41 ()
                                          .setValidationEventHandler (aValidationHdl)
                                          .read (uploadedData);
        break;
      case V42:
        aParsedInvoice = EbInterfaceReader.ebInterface42 ()
                                          .setValidationEventHandler (aValidationHdl)
                                          .read (uploadedData);
        break;
      case V43:
        aParsedInvoice = EbInterfaceReader.ebInterface43 ()
                                          .setValidationEventHandler (aValidationHdl)
                                          .read (uploadedData);
        break;
      case V50:
        aParsedInvoice = EbInterfaceReader.ebInterface50 ()
                                          .setValidationEventHandler (aValidationHdl)
                                          .read (uploadedData);
        break;
      default:
        throw new IllegalStateException ("Internal inconsistency: " + eVersion);
    }

    final Locale aDisplayLocale = Constants.DE_AT;
    final Locale aContentLocale = Constants.DE_AT;

    if (aParsedInvoice == null)
    {
      error ("Die ebInterface-Datei entspricht nicht dem XML Schema und kann daher nicht verarbeitet werden.");
      for (final IError aError : aErrorListEbi.getAllFailures ())
        error ("Fehler: " + aError.getAsString (aDisplayLocale));
      onError ();
      return;
    }

    LOG.info ("Converting ebInterface " + eVersion.getVersion ().getAsString () + " to XRechnung");

    final InvoiceType aUBLInvoice;
    ErrorList aConvertErrorList = new ErrorList ();
    // Convert ebInterface to XRechnung
    switch (eVersion)
    {
      case V40:
        aUBLInvoice = new EbInterface40ToXRechnungUBLConverter (aDisplayLocale,
                                                                aContentLocale).convert ((Ebi40InvoiceType) aParsedInvoice,
                                                                                         aConvertErrorList);
        break;
      case V41:
        aUBLInvoice = new EbInterface41ToXRechnungUBLConverter (aDisplayLocale,
                                                                aContentLocale).convert ((Ebi41InvoiceType) aParsedInvoice,
                                                                                         aConvertErrorList);
        break;
      case V42:
        aUBLInvoice = new EbInterface42ToXRechnungUBLConverter (aDisplayLocale,
                                                                aContentLocale).convert ((Ebi42InvoiceType) aParsedInvoice,
                                                                                         aConvertErrorList);
        break;
      case V43:
        aUBLInvoice = new EbInterface43ToXRechnungUBLConverter (aDisplayLocale,
                                                                aContentLocale).convert ((Ebi43InvoiceType) aParsedInvoice,
                                                                                         aConvertErrorList);
        break;
      case V50:
        aUBLInvoice = new EbInterface50ToXRechnungUBLConverter (aDisplayLocale,
                                                                aContentLocale).convert ((Ebi50InvoiceType) aParsedInvoice,
                                                                                         aConvertErrorList);
        break;
      default:
        throw new IllegalStateException ("This ebInterface version is unknown: " + eVersion);
    }

    final StringBuilder aErrorLog = new StringBuilder ();
    final byte [] aUBLXML;
    if (aConvertErrorList.containsAtLeastOneError ())
    {
      aErrorLog.append ("<b>Bei der ebInterface-XRechnung-Konvertierung sind folgende Fehler aufgetreten:</b><br/>");
      for (final IError error : aConvertErrorList.getAllErrors ())
      {
        aErrorLog.append (error.getErrorFieldName ())
                 .append (":<br/>")
                 .append (error.getErrorText (aDisplayLocale))
                 .append ("<br/><br/>");
      }
      aUBLXML = null;
    }
    else
    {
      LOG.info ("Conversion from ebInterface to XRechnung was successful");
      // No need to collect errors here, because the validation was already
      // performed previously
      aUBLXML = UBL21Writer.invoice ().getAsBytes (aUBLInvoice);
    }

    // Redirect
    setResponsePage (new ResultPageXRechnung (aUBLXML,
                                              aErrorLog.toString (),
                                              this.fromStartPage ? StartPage.class : LabsPage.class));
  }

  /**
   * Process errors
   */
  @Override
  protected void onError ()
  {
    // Show the feedback panel in case on an error
    feedbackPanel.setVisible (true);
  }
}
