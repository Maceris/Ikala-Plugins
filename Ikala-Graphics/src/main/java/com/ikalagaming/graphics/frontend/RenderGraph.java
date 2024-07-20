package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.backend.base.RenderStage;

import lombok.NonNull;

import java.util.List;

public record RenderGraph(@NonNull List<Node> nodes) {

    public record Node(@NonNull List<Attachment> attachments, @NonNull RenderStage.Type type) {}
}
