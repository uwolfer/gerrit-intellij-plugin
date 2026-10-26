#!/usr/bin/env bash
#
# Copyright 2026 Urs Wolfer
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Adds a shop project's worth of review data to the seeded Gerrit of the run-ide
# skill: users alice, bob and carol (password = login), four changes by them with
# votes and inline comments, and two stars for admin. Run once after gerrit.sh start.
set -eEuo pipefail
U=localhost:8080
WORK=$(mktemp -d)
api() { curl -sS --fail-with-body -u "$1" -H 'Content-Type: application/json' -X "$2" -d "$4" "http://$U/a$3" -o /dev/null; }
for u in alice:Alice\ Johnson bob:Bob\ Martinez carol:Carol\ Nguyen; do
    api admin:secret PUT "/accounts/${u%%:*}" "{\"name\":\"${u#*:}\",\"email\":\"${u%%:*}@example.com\",\"http_password\":\"${u%%:*}\"}"
done

git clone -q "http://admin:secret@$U/a/demo" "$WORK/r"; cd "$WORK/r"
curl -sSfLo .git/hooks/commit-msg "http://$U/tools/hooks/commit-msg"; chmod +x .git/hooks/commit-msg
base=$(git rev-parse origin/master)
P=src/main/java/com/example/shop
change() { git checkout -q --detach "$base"; git config user.name "$2"; git config user.email "$3"; mkdir -p $P; }
push() { git push -q "http://$1:$1@$U/a/demo" "HEAD:refs/for/master%$2" 2>&1 | grep -v -e remote: -e warning || true; }

change alice "Alice Johnson" alice@example.com
cat > $P/Cart.java <<'J'
package com.example.shop;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Cart {
    private final List<Item> items = new ArrayList<>();

    public void add(Item item) {
        items.add(item);
    }

    public BigDecimal total() {
        BigDecimal sum = BigDecimal.ZERO;
        for (Item item : items) {
            sum = sum.add(item.price());
        }
        return sum;
    }
}
J
cat > $P/Item.java <<'J'
package com.example.shop;

import java.math.BigDecimal;

public record Item(String sku, String name, BigDecimal price) {
}
J
git add -A; git commit -qm "Add shopping cart

Introduce Cart and Item so that orders can be priced on the server."
push alice r=bob,r=carol,cc=reviewer
cat > $P/Cart.java <<'J'
package com.example.shop;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Cart {
    private final List<Item> items = new ArrayList<>();

    public void add(Item item) {
        items.add(item);
    }

    public List<Item> items() {
        return Collections.unmodifiableList(items);
    }

    public BigDecimal total() {
        return items.stream()
                .map(Item::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
J
git commit -qa --amend --no-edit
push alice r=bob,r=carol,cc=reviewer

change bob "Bob Martinez" bob@example.com
cat > $P/DiscountService.java <<'J'
package com.example.shop;

import java.math.BigDecimal;
import java.util.Map;

public class DiscountService {
    private final Map<String, BigDecimal> codes = Map.of(
            "WELCOME10", new BigDecimal("0.10"),
            "SUMMER25", new BigDecimal("0.25"));

    public BigDecimal apply(String code, BigDecimal total) {
        BigDecimal rate = codes.get(code);
        if (rate == null) {
            return total;
        }
        return total.subtract(total.multiply(rate));
    }
}
J
git add -A; git commit -qm "Support discount codes at checkout"
push bob r=alice,r=carol

change carol "Carol Nguyen" carol@example.com
cat > $P/OrderService.java <<'J'
package com.example.shop;

public class OrderService {
    public String confirmationMail(Order order) {
        return "Thanks " + order.customer().name() + ", your order " + order.id() + " is on its way.";
    }
}
J
git add -A; git commit -qm "Fix NPE in order confirmation mail"
push carol r=alice

change alice "Alice Johnson" alice@example.com
printf 'package com.example.shop;\n\npublic class Inventory {\n    // TODO\n}\n' > $P/Inventory.java
git add -A; git commit -qm "Track stock levels in Inventory"
push alice wip

# Change numbers follow the base seed (1-3), so these are 4 to 7. Code-Review is -1..+1 for
# everyone but admin.
F=$P
api bob:bob POST /changes/demo~4/revisions/current/review '{"message":"Nice cleanup with streams. A couple of small remarks.","labels":{"Code-Review":1},"comments":{"'$F'/Cart.java":[{"line":19,"message":"Should we guard against null prices here? A missing price would blow up the whole total.","unresolved":true},{"line":14,"message":"Good call returning an unmodifiable view."}]}}'
api carol:carol POST /changes/demo~4/revisions/current/review '{"message":"Looks good to me.","labels":{"Code-Review":1}}'
api alice:alice POST /changes/demo~5/revisions/current/review '{"message":"Codes are hard-coded. Can we load them from configuration or the database?","labels":{"Code-Review":-1},"comments":{"'$F'/DiscountService.java":[{"line":9,"message":"Hard-coded discount codes will need a deploy for every campaign.","unresolved":true},{"line":19,"message":"Use setScale(2, RoundingMode.HALF_UP) so prices never end up with more than two decimals.","unresolved":true}]}}'
api alice:alice POST /changes/demo~6/revisions/current/review '{"message":"Thanks, this fixes the crash we saw in production.","labels":{"Code-Review":1}}'
api admin:secret POST /changes/demo~6/revisions/current/review '{"message":"Approved.","labels":{"Code-Review":2}}'
api admin:secret PUT /accounts/self/starred.changes/4 ''
api admin:secret PUT /accounts/self/starred.changes/6 ''
rm -rf "$WORK"
