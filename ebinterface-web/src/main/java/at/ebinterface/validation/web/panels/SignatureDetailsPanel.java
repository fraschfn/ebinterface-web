package at.ebinterface.validation.web.panels;

import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

/**
 * Shows the details of a signature verification (signature, certificate, manifest)
 */
public class SignatureDetailsPanel extends Panel {
  public SignatureDetailsPanel(final String id, final IModel<String> detailsModel, final IModel<Boolean> isValid) {
    super(id);

    add(new Label("status", isValid.getObject().booleanValue () ? "Gültig" : "Ungültig").add(
        new AttributeAppender("class", isValid.getObject().booleanValue () ? Model.of("alert alert-success")
                                                                           : Model.of("alert alert-danger"))));
    add(new Label("details", detailsModel));
  }
}
