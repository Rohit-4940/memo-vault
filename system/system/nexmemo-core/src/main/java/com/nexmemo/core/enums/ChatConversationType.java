package com.nexmemo.core.enums;

/**
 * Kind of a chat conversation. Determines which resource-based authorization rule applies.
 *
 * @author : NexMemo
 **/
public enum ChatConversationType {
    /** One-to-one conversation between two employees. */
    DIRECT,
    /** Named group; membership is explicit via chat_participant. */
    GROUP,
    /** Branch channel; membership derived from user_branches. */
    BRANCH,
    /** Department channel; membership derived from user_departments. */
    DEPARTMENT,
    /** Discussion attached to a memo; access derived from memo visibility. */
    MEMO
}
