package org.poolc.api.gamification.dto;

import lombok.Getter;
import org.poolc.api.gamification.domain.CollectionDraw;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
public class AdminPokemonGrantResponse {
    private final Long drawId;
    private final String memberLoginID;
    private final String memberName;
    private final Long collectibleId;
    private final String pokemonName;
    private final String spriteUrl;
    private final boolean shiny;
    private final String rarity;
    private final String grantorName;
    private final String grantSource;
    private final OffsetDateTime grantedAt;

    public AdminPokemonGrantResponse(CollectionDraw draw, boolean legacySql) {
        this.drawId = draw.getId();
        this.memberLoginID = draw.getMember().getLoginID();
        this.memberName = draw.getMember().getName();
        this.collectibleId = draw.getCollectible().getId();
        this.pokemonName = draw.getCollectible().getNameKo();
        this.spriteUrl = draw.isShiny()
                ? (draw.getCollectible().getShinyDetailSpriteUrl() != null ? draw.getCollectible().getShinyDetailSpriteUrl() : draw.getCollectible().getShinySpriteUrl())
                : (draw.getCollectible().getDetailSpriteUrl() != null ? draw.getCollectible().getDetailSpriteUrl() : draw.getCollectible().getSpriteUrl());
        this.shiny = draw.isShiny();
        this.rarity = draw.getRarityAtDraw().name();
        this.grantorName = draw.getGrantedBy() != null ? draw.getGrantedBy().getName() : (legacySql ? "김민조" : "뽑기");
        this.grantSource = draw.getGrantedBy() != null ? "ADMIN" : (legacySql ? "LEGACY_SQL" : "DRAW");
        this.grantedAt = draw.getDrawnAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    }
}
