package cn.citizenwiki.model.converter;

import cn.citizenwiki.match.rule.MatchRules;
import cn.citizenwiki.match.rule.RuleGroup;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface MatchRulesMapper {
    
    MatchRulesMapper INSTANCE = Mappers.getMapper(MatchRulesMapper.class);
    
    /**
     * 深拷贝 RuleGroup
     */
    RuleGroup copyRuleGroup(RuleGroup source);
    
    /**
     * 深拷贝 MatchRules
     */
    MatchRules copyMatchRules(MatchRules source);
}
