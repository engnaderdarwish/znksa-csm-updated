package com.cmak.integrations.bundle.salam.workinfo;

import com.bmc.arsys.rx.services.action.domain.Action;
import com.bmc.arsys.rx.services.action.domain.ActionParameter;
import com.bmc.arsys.rx.services.common.Service;
import com.bmc.arsys.rx.services.common.domain.Scope;
import com.cmak.integrations.bundle.salam.common.SalamSnFields;
import com.cmak.integrations.bundle.salam.common.ServiceNowSoapClient;
import jakarta.validation.constraints.NotBlank;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.cmak.integrations.bundle.salam.workinfo.WorkInfoTicketData.orEmpty;


public class WorkInfoService implements Service {

    private final WorkInfoRemedyGateway remedyGateway = new WorkInfoRemedyGateway();
    private final WorkInfoConfigProvider config = new WorkInfoConfigProviderImpl();

    @Action(name = "salamCreateWorkInfo", scope = Scope.PUBLIC)
    public String salamCreateWorkInfo(
            @ActionParameter(name = "salamGuid") @NotBlank String salamGuid,
            @ActionParameter(name = "timeoutMs") int timeoutMs) {
        try {
            applyTrustStore();
            WorkInfoTicketData data = remedyGateway.fetchByGuid(salamGuid, config);
            Map<String, String> fields = buildFields(data);
            Map<String, String> result = ServiceNowSoapClient.insert(
                    config.getWorkInfoApiUrl(), config.getSalamUser(), config.getSalamPassword(), fields, timeoutMs);
            return ServiceNowSoapClient.summarize(result);
        } catch (Exception e) {
            throw new RuntimeException("SALAM WorkInfo creation failed for GUID " + salamGuid, e);
        }
    }

    private void applyTrustStore() {
        String cert = config.getSalamCertPath();
        if (cert == null || cert.isBlank()) return; // ServiceNow cert is public-CA signed: default JVM trust is enough
        System.setProperty("javax.net.ssl.trustStore", cert);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }

    private Map<String, String> buildFields(WorkInfoTicketData data) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put(SalamSnFields.KEY_TICKET, orEmpty(data.getZainTicketNo()));
        f.put(SalamSnFields.INTEGRATION_SRC, SalamSnFields.SOURCE_VALUE);
        f.put(SalamSnFields.WORK_NOTES, orEmpty(data.getDetailedDescription()));
        if (data.getAttachmentBase64() != null && !data.getAttachmentBase64().isEmpty()) {
            f.put(SalamSnFields.CONTENT_BASE64, data.getAttachmentBase64());
            f.put(SalamSnFields.FILE_NAME, orEmpty(data.getAttachmentName()));
        }
        return f;
    }
}
