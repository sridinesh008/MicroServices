import { useState } from "react";
import "./commandFeed.css";
import TopBar from "./TopBar";
import Feed from "./Feed";
import PendingCard from "./PendingCard";
import FiltersRulesDrawer from "./FiltersRulesDrawer";
import { useChat } from "../../design/useChat";
import { useDrafts } from "../../design/useDrafts";
import { useDashboardData } from "../../design/useDashboardData";

export default function CommandFeedApp() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const drafts = useDrafts();
  const { messages, streaming, sendChat } = useChat(drafts.refresh);
  const d = useDashboardData();

  return (
    <div className="skin-command-feed">
      <TopBar
        onDrawerToggle={() => setDrawerOpen((v) => !v)}
        onSend={sendChat}
        sending={streaming}
        aggregates={d.aggregates}
        categories={d.categories}
        onDraft={drafts.addDrafts}
      />
      {drafts.drafts.length > 0 && (
        <div className="cf-feed-wrap" style={{ paddingBottom: 0 }}>
          <div className="cf-date-divider">Needs review</div>
          {drafts.drafts.map((item) => (
            <PendingCard key={item.id} item={item} onDone={() => drafts.removeDraft(item.id)} />
          ))}
        </div>
      )}
      <Feed
        expenses={d.page?.content ?? []}
        messages={messages}
        allCategories={d.categories}
        onExpenseUpdated={d.onExpenseUpdated}
        loading={d.initialLoading}
      />
      <FiltersRulesDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        filters={d.filters}
        categories={d.categories}
        onFilterChange={d.onFilterChange}
        onReapplyDone={d.refresh}
      />
    </div>
  );
}
