package io.testopsai.server.modules.identity;

import io.testopsai.server.platform.security.CurrentUserResolver;
import io.testopsai.server.platform.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/platform/accounts")
class PlatformAccountController {
  private final AccountService accounts;
  private final CurrentUserResolver users;

  PlatformAccountController(AccountService accounts, CurrentUserResolver users) {
    this.accounts = accounts;
    this.users = users;
  }

  @GetMapping
  PageResponse<Account> listAccounts(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return accounts.listAccounts(users.resolve(authentication), page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  Account createAccount(
      Authentication authentication,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody AccountService.AccountSeed body) {
    return accounts.createAccount(users.resolve(authentication), key, body);
  }

  @PatchMapping("/{userId}")
  Account setAccountStatus(
      Authentication authentication,
      @PathVariable long userId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody AccountService.AccountStatus body) {
    return accounts.setAccountStatus(users.resolve(authentication), userId, key, body);
  }

  @PostMapping("/{userId}/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void resetPassword(
      Authentication authentication,
      @PathVariable long userId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody AccountService.TempPassword body) {
    accounts.resetPassword(users.resolve(authentication), userId, key, body);
  }
}
