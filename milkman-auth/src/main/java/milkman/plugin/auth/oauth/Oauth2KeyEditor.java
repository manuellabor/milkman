package milkman.plugin.auth.oauth;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.scene.Node;
import milkman.domain.KeySet.KeyEntry;
import milkman.plugin.auth.oauth.GrantTypeBuilder.ClientCredentialBuilder;
import milkman.plugin.auth.oauth.GrantTypeBuilder.PasswordBuilder;
import milkman.plugin.auth.oauth.model.OAuth2Token;
import milkman.plugin.auth.oauth.model.Oauth2Credentials;
import milkman.ui.main.Toaster;
import milkman.ui.plugin.KeyEditor;
import milkman.ui.plugin.ToasterAware;
import milkman.utils.fxml.FxmlBuilder.*;
import milkman.utils.fxml.GenericBinding;

import java.util.UUID;

import static milkman.plugin.auth.oauth.GrantTypeBuilder.AuthorizationCodeBuilder;
import static milkman.utils.fxml.FxmlBuilder.*;

public class Oauth2KeyEditor implements KeyEditor<Oauth2Credentials>, ToasterAware {

    private final GenericBinding<Oauth2Credentials, String> nameBinding = GenericBinding.of(Oauth2Credentials::getName, Oauth2Credentials::setName);
    private final GenericBinding<Oauth2Credentials, String> endpointBinding = GenericBinding.of(Oauth2Credentials::getAccessTokenEndpoint, Oauth2Credentials::setAccessTokenEndpoint);
    private final GenericBinding<Oauth2Credentials, String> clientIdBinding = GenericBinding.of(Oauth2Credentials::getClientId, Oauth2Credentials::setClientId);
    private final GenericBinding<Oauth2Credentials, String> clientSecretBinding = GenericBinding.of(Oauth2Credentials::getClientSecret, Oauth2Credentials::setClientSecret);
    private final GenericBinding<Oauth2Credentials, String> scopesBinding = GenericBinding.of(Oauth2Credentials::getScopes, Oauth2Credentials::setScopes);
    private final GenericBinding<Oauth2Credentials, Boolean> autoRefreshBinding = GenericBinding.of(Oauth2Credentials::isAutoRefresh, Oauth2Credentials::setAutoRefresh);

    private Toaster toaster;
    private JFXTextField txtRefreshToken;
    private JFXTextField txtExpires;
    private JFXTextField txtAccessToken;


    @Override
    public String getName() {
        return "Oauth2 Credentials";
    }

    @Override
    public Node getRoot(Oauth2Credentials keyEntry) {
        var root = new VboxExt();
        root.setSpacing(25);

        root.add(formEntry("Name", nameBinding, keyEntry));
        root.add(formEntry("Token Endpoint", endpointBinding, keyEntry));
        root.add(formEntry("Client Id", clientIdBinding, keyEntry));
        root.add(formEntry("Client Secret", clientSecretBinding, keyEntry));
        root.add(formEntry("Scopes", scopesBinding, keyEntry));
        var autoRefresh = root.add(new JFXCheckBox("Refresh Token on expiry"));
        autoRefreshBinding.bindTo(autoRefresh.selectedProperty(), keyEntry);

        var combobox = root.add(new JFXComboBox<GrantTypeBuilder>());
        combobox.getItems().addAll(
                new ClientCredentialBuilder(),
                new PasswordBuilder(),
                new AuthorizationCodeBuilder());

        var grantArea = root.add(vbox());

        root.add(vbox(label("Grant Type"), combobox, grantArea));

        combobox.valueProperty().addListener(((observable, oldValue, newValue) -> {
            Platform.runLater(() -> {
                grantArea.getChildren().clear();
                grantArea.add(newValue.getEditor(keyEntry));
            });
        }));

        var activeBuilder = combobox.getItems().stream()
                .filter(b -> b.supportedGrantType().isInstance(keyEntry.getGrantType()))
                .findAny()
                .orElse(combobox.getItems().get(0));
        combobox.setValue(activeBuilder);

        var btnFetchToken = root.add(button("Fetch Token", () -> fetchToken(keyEntry)));
        btnFetchToken.getStyleClass().add("primary-button");

        this.txtAccessToken = root.add(text("accessToken", "Access Token", true));
        txtAccessToken.setEditable(false);

        this.txtExpires = root.add(text("expires-at", "Expires", true));
        txtExpires.setEditable(false);

        this.txtRefreshToken = root.add(text("refreshToken", "Refresh Token", true));
        txtRefreshToken.setEditable(false);

        showTokenDetails(keyEntry.getToken());

        return root;
    }

    private void fetchToken(Oauth2Credentials keyEntry) {
        if (keyEntry.getGrantType() == null) {
            toaster.showToast("No Granttype chosen");
            return;
        }
        try {
            keyEntry.fetchNewToken();
        } catch (Exception e) {
            toaster.showToast(e.getMessage());
        }
        showTokenDetails(keyEntry.getToken());
    }

    private void showTokenDetails(OAuth2Token token) {
        if (token != null) {
            txtAccessToken.setText(token.getAccessToken());
            txtExpires.setText(token.getExpiresAt().toString());
            txtRefreshToken.setText(token.getRefreshToken());
        } else {
            txtAccessToken.setText("-");
            txtExpires.setText("-");
            txtRefreshToken.setText("-");
        }
    }

    @Override
    public boolean supportsKeyType(KeyEntry keyEntry) {
        return keyEntry instanceof Oauth2Credentials;
    }

    @Override
    public Oauth2Credentials getNewKeyEntry() {
        return new Oauth2Credentials(UUID.randomUUID().toString(), "");
    }

    @Override
    public void setToaster(Toaster toaster) {
        this.toaster = toaster;
    }

}
