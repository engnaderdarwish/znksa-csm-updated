package com.cmak.integrations.bundle.salam.statusupdate;

import com.bmc.arsys.rx.services.action.domain.Action;
import com.bmc.arsys.rx.services.action.domain.ActionParameter;
import com.bmc.arsys.rx.services.common.Service;
import com.bmc.arsys.rx.services.common.domain.Scope;
import com.cmak.integrations.bundle.salam.common.SalamSnFields;
import com.cmak.integrations.bundle.salam.common.ServiceNowSoapClient;
import jakarta.validation.constraints.NotBlank;



import java.util.LinkedHashMap;
import java.util.Map;

import static com.cmak.integrations.bundle.salam.statusupdate.StatusUpdateTicketData.orEmpty;


public class StatusUpdateService implements Service {

    private final StatusUpdateRemedyGateway remedyGateway = new StatusUpdateRemedyGateway();
    private final StatusUpdateConfigProvider config = new StatusUpdateConfigProviderImpl();

    @Action(name = "salamUpdateStatusInProgress", scope = Scope.PUBLIC)
    public String salamUpdateStatusInProgress(
            @ActionParameter(name = "salamGuid") @NotBlank String salamGuid,
            @ActionParameter(name = "timeoutMs") int timeoutMs) {
        try {
            applyTrustStore();
            StatusUpdateTicketData data = remedyGateway.fetchByGuid(salamGuid, config);
            Map<String, String> fields = buildFields(data);
            Map<String, String> result = ServiceNowSoapClient.insert(
                    config.getStatusUpdateApiUrl(), config.getSalamUser(), config.getSalamPassword(), fields, timeoutMs);
            return ServiceNowSoapClient.summarize(result);
        } catch (Exception e) {
            throw new RuntimeException("SALAM In-Progress update failed for GUID " + salamGuid, e);
        }
    }


    private void applyTrustStore() {
        String cert = config.getSalamCertPath();
        if (cert == null || cert.isBlank()) return; // ServiceNow cert is public-CA signed: default JVM trust is enough
        System.setProperty("javax.net.ssl.trustStore", cert);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }

    private Map<String, String> buildFields(StatusUpdateTicketData data) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put(SalamSnFields.KEY_TICKET, orEmpty(data.getZainTicketNo()));
        f.put(SalamSnFields.STATE, SalamSnFields.STATE_IN_PROGRESS);
        f.put(SalamSnFields.ASSIGNED_TO, SalamSnFields.IN_PROGRESS_ASSIGNEE);
        f.put(SalamSnFields.ASSIGNMENT_GROUP, SalamSnFields.IN_PROGRESS_GROUP);
        f.put(SalamSnFields.INTEGRATION_SRC, SalamSnFields.SOURCE_VALUE);
        f.put(SalamSnFields.WORK_NOTES, orEmpty(data.getWorkLog()));
        return f;
    }
}
