package com.campusops.level.service;

import com.campusops.level.dto.LevelRequestDto;
import com.campusops.level.dto.LevelResponseDto;
import com.campusops.level.entity.Level;
import com.campusops.level.mapper.LevelMapper;
import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionImpact;
import com.campusops.level.repository.LevelRepository;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.security.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LevelService {

    private final LevelRepository levelRepository;
    private final LevelMapper levelMapper;
    private final AccessScopeService accessScope;
    private final DeletionAnalyzer deletionAnalyzer;

    public LevelResponseDto createLevel(LevelRequestDto request) {
        accessScope.requireAdmin();
        if (levelRepository.existsByNom(request.getNom())) {
            throw new DuplicateResourceException(
                    "Un niveau avec le nom " + request.getNom() + " existe déjà");
        }

        Level level = levelMapper.toEntity(request);
        level.setActif(true);

        Level saved = levelRepository.save(level);
        return levelMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public LevelResponseDto getLevelById(Long id) {
        return levelMapper.toResponseDto(findLevelOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<LevelResponseDto> filterLevels(Boolean actif) {
        List<Level> levels = (actif != null)
                ? levelRepository.findByActif(actif)
                : levelRepository.findAllByOrderByOrdreAsc();
        return levels.stream().map(levelMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<LevelResponseDto> searchLevels(String keyword) {
        return levelRepository.searchByKeyword(keyword).stream()
                .map(levelMapper::toResponseDto)
                .toList();
    }

    public LevelResponseDto updateLevel(Long id, LevelRequestDto request) {
        accessScope.requireAdmin();
        Level level = findLevelOrThrow(id);

        if (levelRepository.existsByNomAndIdNot(request.getNom(), id)) {
            throw new DuplicateResourceException(
                    "Un niveau avec le nom " + request.getNom() + " existe déjà");
        }

        level.setNom(request.getNom());
        level.setOrdre(request.getOrdre());
        level.setTypeFormation(request.getTypeFormation());
        level.setNombreAnnees(request.getNombreAnnees());

        Level updated = levelRepository.save(level);
        return levelMapper.toResponseDto(updated);
    }

    /**
     * Compteurs d'impact avant suppression : usages metier qui bloquent la
     * suppression du niveau (filieres, promotions, semestres, seances, emplois
     * du temps). Aucun enfant supprime en cascade. Sert a la confirmation.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findLevelOrThrow(id));
    }

    /**
     * Supprime un niveau / cycle. Referentiel taxonomique : aucune cascade. La
     * suppression est refusee, avec un message metier explicite, si des filieres,
     * promotions, semestres, seances ou emplois du temps y sont encore rattaches
     * (« suppression apres modification »). Le parametre {@code cascade} est sans
     * effet ici (aucun enfant structurel) : il n'existe que pour l'uniformite de
     * l'API de suppression.
     */
    public void deleteLevel(Long id, boolean cascade) {
        accessScope.requireAdmin();
        Level level = findLevelOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(level);
        impact.requireConfirmed(cascade);
        levelRepository.delete(level);
    }

    // Volontairement, pas de deactivateLevel/activateLevel : le niveau / cycle est
    // un referentiel taxonomique stable, partage par toutes les filieres. Son
    // drapeau actif n'entre dans aucun predicat d'usabilite (voir ReferentialStatus)
    // et le desactiver n'aurait aucun effet metier coherent — il ne ferait que le
    // masquer des selects, sans cascade ni garde. On ne l'expose donc pas (§9/§26).
    // Le champ actif reste a true par defaut ; un niveau obsolete se supprime.

    private Level findLevelOrThrow(Long id) {
        return levelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Niveau introuvable avec l'id " + id));
    }
}
