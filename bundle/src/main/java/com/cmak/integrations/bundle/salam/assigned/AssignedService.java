package com.cmak.integrations.bundle.salam.assigned;

import com.bmc.arsys.rx.services.action.domain.Action;
import com.bmc.arsys.rx.services.action.domain.ActionParameter;
import com.bmc.arsys.rx.services.common.Service;
import com.bmc.arsys.rx.services.common.domain.Scope;
import com.cmak.integrations.bundle.salam.common.SalamSnFields;
import com.cmak.integrations.bundle.salam.common.ServiceNowSoapClient;
import jakarta.validation.constraints.NotBlank;


import java.util.LinkedHashMap;
import java.util.Map;

import static com.cmak.integrations.bundle.salam.assigned.AssignedTicketData.orEmpty;


public class AssignedService implements Service {

    private final AssignedRemedyGateway remedyGateway = new AssignedRemedyGateway();
    private final AssignedConfigProvider config = new AssignedConfigProviderImpl();

    @Action(name = "salamAssignedTicket", scope = Scope.PUBLIC)
    public String salamAssignedTicket(
            @ActionParameter(name = "salamGuid") @NotBlank String salamGuid,
            @ActionParameter(name = "timeoutMs") int timeoutMs) {
        try {
            applyTrustStore();
            AssignedTicketData data = remedyGateway.fetchByGuid(salamGuid, config);
            Map<String, String> fields = buildFields(data);
            Map<String, String> result = ServiceNowSoapClient.insert(
                    config.getAssignedApiUrl(), config.getSalamUser(), config.getSalamPassword(), fields, timeoutMs);
            return ServiceNowSoapClient.summarize(result);
        } catch (Exception e) {
            throw new RuntimeException("SALAM Assigned update failed for GUID " + salamGuid, e);
        }
    }


    private void applyTrustStore() {
        String cert = config.getSalamCertPath();
        if (cert == null || cert.isBlank()) return; // ServiceNow cert is public-CA signed: default JVM trust is enough
        System.setProperty("javax.net.ssl.trustStore", cert);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }

    private Map<String, String> buildFields(AssignedTicketData data) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put(SalamSnFields.KEY_TICKET, orEmpty(data.getZainTicketNo()));
        f.put(SalamSnFields.STATE, SalamSnFields.STATE_ASSIGNED);
        f.put(SalamSnFields.ASSIGNED_TO, SalamSnFields.CC_ASSIGNEE);
        f.put(SalamSnFields.ASSIGNMENT_GROUP, SalamSnFields.CC_GROUP);
        f.put(SalamSnFields.INTEGRATION_SRC, SalamSnFields.SOURCE_VALUE);
        return f;
    }
}
