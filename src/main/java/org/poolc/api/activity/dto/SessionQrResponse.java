package org.poolc.api.activity.dto;

import lombok.Value;

@Value
public class SessionQrResponse {
    String checkInUrl;
    String qrImageDataUrl;
}
