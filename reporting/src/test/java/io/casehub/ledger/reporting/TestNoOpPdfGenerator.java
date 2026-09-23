package io.casehub.ledger.reporting;

import java.util.Optional;

import io.casehub.platform.api.pdf.PdfGenerator;
import io.casehub.platform.api.pdf.PdfOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;

@ApplicationScoped
@Alternative
@Priority(1)
public class TestNoOpPdfGenerator implements PdfGenerator {

    @Override
    public Optional<byte[]> generateFromHtml(final String html, final PdfOptions options) {
        return Optional.empty();
    }
}
