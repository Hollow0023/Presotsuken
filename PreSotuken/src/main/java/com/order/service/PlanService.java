package com.order.service; // 適切なパッケージに配置してね

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.order.dto.PlanRequestDto;
import com.order.dto.PlanResponseDto;
import com.order.entity.MenuGroup; // PlanResponseDtoでmenuGroupNamesを取得するため
import com.order.entity.Plan;
import com.order.entity.PlanMenuGroupMap;
import com.order.repository.MenuGroupRepository; // MenuGroupの名前を取得するため
import com.order.repository.PlanMenuGroupMapRepository;
import com.order.repository.PlanRepository;
import com.order.repository.StoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final MenuGroupRepository menuGroupRepository; // MenuGroupの名前解決用
    private final PlanMenuGroupMapRepository planMenuGroupMapRepository;
    private final StoreRepository storeRepository;

    // プラン一覧取得
    public List<PlanResponseDto> getAllPlans(Integer storeId) {
        List<Plan> plans = planRepository.findByStore_StoreId(storeId); // Storeとの関連付けがあれば

        return plans.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    // 特定のプラン取得（編集用）
    public Optional<PlanResponseDto> getPlanById(Integer planId) {
        return planRepository.findById(planId).map(this::convertToDto);
    }

    // プランの新規作成
    @Transactional
    public PlanResponseDto createPlan(PlanRequestDto requestDto) {
        Plan plan = new Plan();
        plan.setPlanName(requestDto.getPlanName());
        plan.setPlanDescription(requestDto.getPlanDescription());
        // 店舗との関連付けが必要
        // plan.setStore(storeRepository.findById(requestDto.getStoreId()).orElseThrow());
        // ここではstoreIdはIntegerで受け取っているので、後でControllerでStoreエンティティを設定するか、
        // PlanServiceでStoreRepositoryを注入して取得する
        plan.setStore(storeRepository.findById(requestDto.getStoreId()).orElseThrow());

        Plan savedPlan = planRepository.save(plan);

        // 紐づくメニューグループを保存
        updatePlanMenuGroupMaps(savedPlan.getPlanId(), requestDto.getMenuGroupIds());

        return convertToDto(savedPlan);
    }

    // プランの更新
    @Transactional
    public PlanResponseDto updatePlan(PlanRequestDto requestDto) {
        Plan existingPlan = planRepository.findById(requestDto.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("指定されたプランが見つかりません。"));

        existingPlan.setPlanName(requestDto.getPlanName());
        existingPlan.setPlanDescription(requestDto.getPlanDescription());
        // storeIdは更新しない想定

        Plan updatedPlan = planRepository.save(existingPlan);

        // 紐づくメニューグループを更新
        updatePlanMenuGroupMaps(updatedPlan.getPlanId(), requestDto.getMenuGroupIds());

        return convertToDto(updatedPlan);
    }

    // プランの削除
    @Transactional
    public void deletePlan(Integer planId) {
        // 紐づくPlanMenuGroupMapを先に削除
        planMenuGroupMapRepository.deleteByPlanId(planId);
        // プランを削除
        planRepository.deleteById(planId);
    }

    // DTO変換ヘルパーメソッド
    private PlanResponseDto convertToDto(Plan plan) {
        PlanResponseDto dto = new PlanResponseDto();
        dto.setPlanId(plan.getPlanId());
        dto.setPlanName(plan.getPlanName());
        dto.setPlanDescription(plan.getPlanDescription());
        dto.setStoreId(plan.getStore().getStoreId());

        // 紐づくメニューグループのIDと名前を取得
        List<PlanMenuGroupMap> maps = planMenuGroupMapRepository.findByPlanId(plan.getPlanId());
        List<Integer> menuGroupIds = maps.stream()
                .map(PlanMenuGroupMap::getMenuGroupId)
                .collect(Collectors.toList());
        dto.setMenuGroupIds(menuGroupIds);

        // メニューグループ名を取得 (一括取得が効率的)
        if (!menuGroupIds.isEmpty()) {
            List<MenuGroup> menuGroups = menuGroupRepository.findAllById(menuGroupIds);
            List<String> menuGroupNames = menuGroups.stream()
                    .map(MenuGroup::getGroupName)
                    .collect(Collectors.toList());
            dto.setMenuGroupNames(menuGroupNames);
        } else {
            dto.setMenuGroupNames(List.of());
        }

        return dto;
    }

 // PlanMenuGroupMapの更新（新規作成・削除）ヘルパーメソッド
    private void updatePlanMenuGroupMaps(Integer planId, List<Integer> newMenuGroupIds) {
        // ① まず、更新前の既存の紐付け情報を取得しておく
        List<PlanMenuGroupMap> oldMaps = planMenuGroupMapRepository.findByPlanId(planId);
        List<Integer> oldMenuGroupIds = oldMaps.stream()
                                                .map(PlanMenuGroupMap::getMenuGroupId)
                                                .collect(Collectors.toList());

        // ② 既存の紐付けを全て削除
        planMenuGroupMapRepository.deleteByPlanId(planId); 

        // ③ 新しい紐付けを追加
        if (newMenuGroupIds != null && !newMenuGroupIds.isEmpty()) {
            List<PlanMenuGroupMap> mapsToSave = newMenuGroupIds.stream()
                    .map(menuGroupId -> new PlanMenuGroupMap(planId, menuGroupId))
                    .collect(Collectors.toList());
            planMenuGroupMapRepository.saveAll(mapsToSave);
        }

        // ④ 関連付けが解除されたグループの isPlanTarget を false に設定
        // oldMenuGroupIds にあって、newMenuGroupIds にないものが「解除された」グループ
        oldMenuGroupIds.forEach(oldGroupId -> {
            // 今回の更新でこのプランから紐付けが解除されたかチェック
            boolean isRemovedFromThisPlan = (newMenuGroupIds == null || !newMenuGroupIds.contains(oldGroupId));

            // 他のプランには紐づいていない かつ 今回の更新でこのプランからも紐付け解除された場合
            if (isRemovedFromThisPlan) { // この条件だけじゃ足りない。他のプランに紐づいてないことを確認しないと
                boolean isStillAssociatedWithOtherPlans = planMenuGroupMapRepository.existsByMenuGroupIdAndPlanIdNot(oldGroupId, planId);
                if (!isStillAssociatedWithOtherPlans) {
                    menuGroupRepository.findById(oldGroupId).ifPresent(group -> {
                        if (group.getIsPlanTarget() != null && group.getIsPlanTarget()) {
                            group.setIsPlanTarget(false);
                            menuGroupRepository.save(group);
                        }
                    });
                }
            }
        });

        // ⑤ 新しく紐付けられたグループの isPlanTarget を true に設定
        if (newMenuGroupIds != null) {
            newMenuGroupIds.forEach(newGroupId -> {
                menuGroupRepository.findById(newGroupId).ifPresent(group -> {
                    if (group.getIsPlanTarget() == null || !group.getIsPlanTarget()) {
                        group.setIsPlanTarget(true);
                        menuGroupRepository.save(group);
                    }
                });
            });
        }
    }
}